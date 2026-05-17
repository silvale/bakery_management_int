# 🚀 Quick Start — Test API

## Bước 1: Start PostgreSQL

```bash
cd docker
docker compose up -d
```

Kiểm tra:
```bash
docker ps   # bakery-postgres phải RUNNING
```

---

## Bước 2: Chuẩn bị thư mục file input

```bash
mkdir -p batch-input
```

Copy 4 file Excel vào thư mục này:
```
batch-input/
├── BanhRaNgay.xlsx
├── XuatRa.xlsx
├── BaoCaoNgay.xlsx
└── BigProductBySaleByCat.xlsx   ← phải là .xlsx (không phải .xls)
```

> **Lưu ý:** Thư mục `batch-input` đặt tại root của project
> (cùng cấp với `bakery-api`, `bakery-batch`, `docker`).

---

## Bước 3: Build project

```bash
cd bakery-management
mvn clean install -DskipTests
```

---

## Bước 4: Chạy API

```bash
cd bakery-api
mvn spring-boot:run
```

Hoặc với env variable custom:
```bash
BATCH_INPUT_DIR=./batch-input mvn spring-boot:run
```

Flyway tự động chạy V1, V2, V3 migration khi start.

Kiểm tra startup:
```
Started BakeryApiApplication in X seconds
```

---

## Bước 5: Gọi API

### 🔵 POST /batch/run — Đọc file & Reconcile

```bash
# Chạy với ngày trong file (8/4/2026)
curl -X POST http://localhost:8080/batch/run \
  -H "Content-Type: application/json" \
  -d '{
    "processDate": "2026-04-08",
    "triggeredBy": "test"
  }'
```

Hoặc không cần body (dùng ngày hôm nay):
```bash
curl -X POST http://localhost:8080/batch/run
```

**Response mẫu:**
```json
{
  "reconDate": "2026-04-08",
  "batchRunId": "uuid...",
  "batchStatus": "COMPLETED",
  "isRerun": false,
  "summary": {
    "totalProducts": 8,
    "okCount": 5,
    "discrepancyCount": 2,
    "pendingCount": 1,
    "totalRevenue": 1250000,
    "totalSalesCost": 680000,
    "totalCancelledCost": 15000,
    "totalGrossProfit": 555000
  },
  "products": [
    {
      "productCode": "SP022575",
      "productName": "chén trứng",
      "unit": "PCS",
      "qtyRequested": 30,
      "qtyProduced": 30,
      "productionDiff": 0,
      "productionStatus": "OK",
      "qtySent": 30,
      "qtyReceived": 20,
      "deliveryDiff": 10,
      "deliveryStatus": "DISCREPANCY",
      "qtySoldPos": 4,
      "qtySoldDerived": 4,
      "posDiff": 0,
      "posStatus": "OK",
      "costPerUnit": 2910.18,
      "revenue": 60000,
      "grossProfit": 48360,
      "overallStatus": "DISCREPANCY",
      "discrepancyNote": "Chênh lệch vận chuyển"
    }
  ],
  "fileImports": [
    {
      "fileName": "BanhRaNgay.xlsx",
      "fileType": "PRODUCTION_REQUEST",
      "status": "SUCCESS",
      "rowsTotal": 8,
      "rowsOk": 8,
      "rowsError": 0
    }
  ]
}
```

---

### 🟢 GET /batch/result?date=2026-04-08 — Xem kết quả đã có

```bash
curl "http://localhost:8080/batch/result?date=2026-04-08"
```

---

### 📖 Swagger UI

```
http://localhost:8080/swagger-ui.html
```

---

## Kết quả đối chiếu — Ý nghĩa các trường

| Tầng | Field | Ý nghĩa |
|------|-------|---------|
| Tầng 1 | `productionStatus` | OK = bếp làm đúng số lượng yêu cầu |
| Tầng 1 | `productionDiff` | Dương = làm dư, Âm = làm thiếu |
| Tầng 2 | `deliveryStatus` | OK = cửa hàng nhận đủ từ bếp |
| Tầng 2 | `deliveryDiff` | Dương = thất thoát vận chuyển |
| Tầng 3 | `posStatus` | OK = máy POS khớp với kiểm kê tay |
| Tầng 3 | `qtySoldDerived` | = opening + received - cancelled - closing |
| Lợi nhuận | `grossProfit` | revenue - salesCost - cancelledCost |

---

## Troubleshooting

**Lỗi: File not found**
```
Kiểm tra đường dẫn: batch-input/BanhRaNgay.xlsx có tồn tại không
Hoặc set env: BATCH_INPUT_DIR=/absolute/path/to/batch-input
```

**Lỗi: Không tìm thấy kho tổng**
```
Kiểm tra DB: SELECT * FROM branch;
Phải có 1 record có is_main = true
```

**Lỗi: product not found**
```
Kiểm tra mã SP trong file khớp với bảng product:
SELECT code, name FROM product;
```
