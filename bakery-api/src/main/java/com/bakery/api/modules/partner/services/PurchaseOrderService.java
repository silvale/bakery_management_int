package com.bakery.api.modules.partner.services;

import com.bakery.api.framework.*;
import com.bakery.api.framework.enums.PaymentStatus;
import com.bakery.api.framework.enums.StockLotStatus;
import com.bakery.api.framework.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import com.bakery.api.modules.inventory.entities.IngredientStock;
import com.bakery.api.modules.inventory.entities.StockLot;
import com.bakery.api.modules.inventory.repositories.IngredientStockRepository;
import com.bakery.api.modules.inventory.repositories.StockLotRepository;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.masterdata.entities.Ingredient;
import com.bakery.api.modules.masterdata.entities.UnitConversion;
import com.bakery.api.modules.masterdata.repositories.BranchRepository;
import com.bakery.api.modules.masterdata.repositories.IngredientRepository;
import com.bakery.api.modules.masterdata.repositories.UnitConversionRepository;
import com.bakery.api.modules.partner.entities.PurchaseOrder;
import com.bakery.api.modules.partner.entities.PurchaseOrderLine;
import com.bakery.api.modules.partner.entities.Supplier;
import com.bakery.api.modules.partner.repositories.PurchaseOrderLineRepository;
import com.bakery.api.modules.partner.repositories.PurchaseOrderRepository;
import com.bakery.api.modules.partner.repositories.SupplierRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final SupplierRepository             supplierRepository;
    private final PurchaseOrderRepository        purchaseOrderRepository;
    private final PurchaseOrderLineRepository    purchaseOrderLineRepository;
    private final IngredientRepository           ingredientRepository;
    private final UnitConversionRepository       unitConversionRepository;
    private final StockLotRepository             stockLotRepository;
    private final IngredientStockRepository      ingredientStockRepository;
    private final BranchRepository               branchRepository;

    // -------------------------------------------------------
    //  Tạo đơn nhập hàng mới
    // -------------------------------------------------------
    @Transactional
    public PurchaseOrder createOrder(CreateOrderRequest req) {
        Supplier supplier = supplierRepository.findById(req.supplierId())
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy NCC: " + req.supplierId()));

        Branch branch = branchRepository.findByIsMainTrue()
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho tổng"));

        // Sinh mã đơn: PO-{yyyyMMdd}-{seq 3 chữ số}
        String code = generateOrderCode(req.orderDate());

        PurchaseOrder order = PurchaseOrder.builder()
            .code(code)
            .supplier(supplier)
            .branch(branch)
            .orderDate(req.orderDate())
            .note(req.note())
            .paymentStatus(PaymentStatus.UNPAID)
            .build();

        purchaseOrderRepository.save(order);
        log.info("Tạo đơn nhập hàng: {} | NCC: {}", code, supplier.getName());
        return order;
    }

    // -------------------------------------------------------
    //  Xác nhận nhận hàng → tạo StockLot + cập nhật tồn kho
    // -------------------------------------------------------
    @Transactional
    public void receiveOrder(UUID orderId, List<ReceiveLineRequest> lines) {
        PurchaseOrder order = purchaseOrderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn: " + orderId));

        order.setReceivedDate(LocalDate.now());
        BigDecimal total = BigDecimal.ZERO;

        for (ReceiveLineRequest lineReq : lines) {
            // Hỗ trợ cả ingredientCode (string) và ingredientId (UUID)
            Ingredient ingredient = (lineReq.ingredientCode() != null && !lineReq.ingredientCode().isBlank())
                ? ingredientRepository.findByCode(lineReq.ingredientCode())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy NL: " + lineReq.ingredientCode()))
                : ingredientRepository.findById(lineReq.ingredientId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy NL: " + lineReq.ingredientId()));

            // Tính qty_in_base_unit qua UnitConversion
            UnitConversion conversion = unitConversionRepository
                .findByIngredientIdAndPurchaseUnit(ingredient.getId(), lineReq.purchaseUnit())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Không tìm thấy quy đổi: " + ingredient.getCode() + " / " + lineReq.purchaseUnit()));

            BigDecimal qtyInBaseUnit = lineReq.qtyReceived()
                .multiply(conversion.getBaseQuantity());

            // Tính price_per_base_unit
            BigDecimal pricePerBase = lineReq.unitPrice()
                .divide(conversion.getBaseQuantity(), 6, RoundingMode.HALF_UP);

            // Tạo và save PurchaseOrderLine trước (StockLot cần FK hợp lệ)
            PurchaseOrderLine line = PurchaseOrderLine.builder()
                .purchaseOrder(order)
                .ingredient(ingredient)
                .purchaseUnit(lineReq.purchaseUnit())
                .qtyOrdered(lineReq.qtyOrdered())
                .qtyReceived(lineReq.qtyReceived())
                .unitPrice(lineReq.unitPrice())
                .qtyInBaseUnit(qtyInBaseUnit)
                .note(lineReq.note())
                .build();

            purchaseOrderLineRepository.save(line); // phải save trước khi StockLot tham chiếu
            order.getLines().add(line);
            total = total.add(lineReq.qtyReceived().multiply(lineReq.unitPrice()));

            // Tạo StockLot với barcode tự sinh
            String barcode = generateBarcode(LocalDate.now());
            StockLot lot = StockLot.builder()
                .barcode(barcode)
                .purchaseOrderLine(line)
                .ingredient(ingredient)
                .branch(order.getBranch())
                .qtyInBaseUnit(qtyInBaseUnit)
                .qtyRemaining(qtyInBaseUnit)
                .unitPrice(lineReq.unitPrice())
                .pricePerBaseUnit(pricePerBase)
                .receivedDate(LocalDate.now())
                .expiryDate(lineReq.expiryDate())
                .status(StockLotStatus.AVAILABLE)
                .build();

            stockLotRepository.save(lot);

            // Cập nhật IngredientStock
            int updated = ingredientStockRepository.updateStock(
                ingredient.getId(), order.getBranch().getId(), qtyInBaseUnit
            );

            if (updated == 0) {
                // Chưa có record → tạo mới
                IngredientStock stock = IngredientStock.builder()
                    .ingredient(ingredient)
                    .branch(order.getBranch())
                    .qtyOnHand(qtyInBaseUnit)
                    .build();
                ingredientStockRepository.save(stock);
            }

            log.info("Nhập kho: {} | {} {} | {} gram | Barcode: {}",
                ingredient.getCode(), lineReq.qtyReceived(), lineReq.purchaseUnit(),
                qtyInBaseUnit, barcode);
        }

        order.setTotalAmount(total);
        purchaseOrderRepository.save(order);
        log.info("Nhận hàng đơn {} xong | Tổng: {} VNĐ", order.getCode(), total);
    }

    // -------------------------------------------------------
    //  Thanh toán
    // -------------------------------------------------------
    @Transactional
    public PurchaseOrder recordPayment(UUID orderId, BigDecimal amount) {
        PurchaseOrder order = purchaseOrderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn: " + orderId));

        order.setPaidAmount(order.getPaidAmount().add(amount));

        if (order.getTotalAmount() != null) {
            if (order.getPaidAmount().compareTo(order.getTotalAmount()) >= 0) {
                order.setPaymentStatus(PaymentStatus.PAID);
            } else {
                order.setPaymentStatus(PaymentStatus.PARTIAL);
            }
        }

        purchaseOrderRepository.save(order);
        log.info("Thanh toán đơn {} | Số tiền: {} | Trạng thái: {}",
            order.getCode(), amount, order.getPaymentStatus());
        return order;
    }

    // -------------------------------------------------------
    //  Xem tồn kho nguyên liệu
    // -------------------------------------------------------
    @Transactional(readOnly = true)
    public List<IngredientStock> getStock() {
        Branch mainBranch = branchRepository.findByIsMainTrue()
            .orElseThrow();
        return ingredientStockRepository
            .findAllWithIngredientByBranchId(mainBranch.getId());
    }

    // -------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------
    private String generateOrderCode(LocalDate date) {
        long seq = purchaseOrderRepository.countByOrderDate(date) + 1;
        return String.format("PO-%s-%03d",
            date.format(DateTimeFormatter.ofPattern("yyyyMMdd")), seq);
    }

    private String generateBarcode(LocalDate date) {
        String random = Long.toHexString(System.nanoTime()).toUpperCase().substring(0, 6);
        return String.format("LOT-%s-%s",
            date.format(DateTimeFormatter.ofPattern("yyyyMMdd")), random);
    }

    // -------------------------------------------------------
    //  Request records
    // -------------------------------------------------------
    public record CreateOrderRequest(
        UUID      supplierId,
        LocalDate orderDate,
        String    note
    ) {}

    public record ReceiveLineRequest(
        UUID       ingredientId,    // tuỳ chọn — dùng khi có UUID
        String     ingredientCode,  // tuỳ chọn — dùng thay ingredientId cho dễ test
        String     purchaseUnit,
        BigDecimal qtyOrdered,
        BigDecimal qtyReceived,
        BigDecimal unitPrice,
        LocalDate  expiryDate,
        String     note
    ) {}

    /** Tạo PO + nhận hàng trong 1 lần — dùng khi hàng đến ngay */
    public record CreateAndReceiveRequest(
        UUID                   supplierId,
        LocalDate              orderDate,
        String                 note,
        List<ReceiveLineRequest> ingredients
    ) {}

    @Transactional
    public PurchaseOrder createAndReceive(CreateAndReceiveRequest req) {
        PurchaseOrder order = createOrder(new CreateOrderRequest(
            req.supplierId(), req.orderDate(), req.note()
        ));
        receiveOrder(order.getId(), req.ingredients());
        return purchaseOrderRepository.findById(order.getId()).orElseThrow();
    }
}
