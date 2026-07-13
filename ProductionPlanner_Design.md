# Production Planner — Tài liệu Thiết kế

> **Mục đích**: Hệ thống tự động gợi ý số lượng bánh cần sản xuất ngày hôm sau, dựa trên tồn kho cuối ngày hôm nay (nhập từ Báo Cáo Ngày).
>
> **Audience**: FE Team
>
> **Last updated**: 2026-07-13

---

## 1. Tổng quan Flow

```
Nhân viên finalize DailyReport (báo cáo ngày)
          ↓
  Hệ thống TỰ ĐỘNG tạo gợi ý kế hoạch SX ngày mai
  ProductionPlan → status: DRAFT
          ↓
  Chính (manager) review + điều chỉnh số lượng trên hệ thống
          ↓
  [POST] /api/v1/production/plans/{id}/approve
  ProductionPlan → status: APPROVED
          ↓
  Bếp thấy kế hoạch trên hệ thống → sản xuất
  (không export file, mọi thứ trên hệ thống)
```

**Quan trọng:**
- Kế hoạch được **tự động tạo** khi DailyReport finalize — không cần bấm thủ công
- Bếp **chỉ thấy** kế hoạch đã APPROVED — khi còn DRAFT bếp không thấy
- Quản lý (Chính) là người duy nhất có quyền approve

---

## 2. Ba Pattern Sản Xuất

### Pattern 1 — SIMPLE (Threshold Rules)

Áp dụng cho bánh riêng lẻ, không thuộc group nào.

**Cách hoạt động:**
- Mỗi sản phẩm cấu hình nhiều rule theo `dayType` (WEEKDAY / WEEKEND)
- Rule: nếu tồn kho còn lại < `conditionValue` → sản xuất thêm `produceQty`
- Có thể có nhiều rule, sắp xếp theo `sortOrder` (ưu tiên rule khớp đầu tiên)
- `conditionType`: `COUNT` (số lượng tuyệt đối) hoặc `PERCENT` (% so với target)

**Ví dụ cấu hình — Bánh tiramisu:**

| Sort | Condition Type | Condition Value | Produce Qty | Day Type |
|------|----------------|-----------------|-------------|----------|
| 1    | COUNT          | 5               | 24          | WEEKDAY  |
| 2    | COUNT          | 12              | 12          | WEEKDAY  |
| 1    | COUNT          | 8               | 24          | WEEKEND  |

→ Nếu tồn kho còn 3 (weekday): khớp rule sort=1 → sản xuất thêm 24
→ Nếu tồn kho còn 8 (weekday): khớp rule sort=2 → sản xuất thêm 12
→ Nếu tồn kho còn 20 (weekday): không khớp rule nào → không sản xuất

---

### Pattern 2 — FREE_GROUP (Nhóm tự do)

Áp dụng cho nhóm sản phẩm cùng công thức, chỉ khác sốt/màu. Ví dụ: **Pana Cotta**.

**Cách hoạt động:**
- Group cấu hình tổng số lượng target mỗi ngày (ví dụ: 40 cái/ngày)
- Hệ thống tính: `tổng cần sản xuất = target - tổng tồn kho của tất cả sản phẩm trong group`
- FE hiển thị: "Cần làm thêm **X** cái Pana Cotta"
- **Nhân viên tự phân bổ** số lượng từng loại sốt trên màn hình review

**Ví dụ:**

```
Pana Cotta Group — target: 40/ngày (WEEKDAY)
  ├── Pana Cotta xoài      còn: 8
  ├── Pana Cotta chanh dây  còn: 5
  └── Pana Cotta dâu        còn: 3
  ───────────────────────────────
  Tổng tồn: 16 → Cần làm thêm: 24

  FE cho phép nhập:
  ├── xoài:      [10] (nhân viên tự điền)
  ├── chanh dây: [8]
  └── dâu:       [6]
  ─────────────
  Tổng:          24 ✓
```

---

### Pattern 3 — BATCH_FORMULA (Công thức theo cối/lô)

Áp dụng cho sản phẩm sản xuất theo lô cố định. Ví dụ: **Bánh Bắp**.

**Cách hoạt động:**
- 1 cối = trọng lượng cố định (ví dụ: 10kg)
- Mỗi size bánh có trọng lượng cố định (size 12 = 100g, size 14 = 150g, size 18 = 200g, size 20 = 300g)
- Output ratio (số bánh ra từ 1 cối) do nhân viên quyết định, hệ thống chỉ hiển thị suggestion
- **Tổng trọng lượng của tất cả size phải = trọng lượng 1 cối** (validation quan trọng)

**Ví dụ:**

```
Bánh Bắp Group — 1 cối = 10,000g
  ├── Size 12 (100g/cái)
  ├── Size 14 (150g/cái)
  ├── Size 18 (200g/cái)
  └── Size 20 (300g/cái)

Suggestion mặc định (1 cối):
  ├── Size 12: 30 cái × 100g = 3,000g
  ├── Size 14: 20 cái × 150g = 3,000g
  ├── Size 18: 10 cái × 200g = 2,000g
  └── Size 20:  6 cái × 300g = 1,800g  (còn 200g dư → nhân viên điều chỉnh)
  ─────────────────────────────────────
  Tổng: 10,000g = 10kg ✓ (sau khi adjust)

FE phải validate: sum(qty × grams_per_unit) % batch_weight_grams == 0
```

---

## 3. Phân nhóm sản phẩm (item_group vs production_group)

### item_group — Phòng/Bộ phận (catalog dimension)

Dùng để phân loại sản phẩm theo khu vực sản xuất. Dùng để **filter danh sách** trên UI.

| Code | Tên            |
|------|----------------|
| PL   | Phòng Lạnh     |
| PK   | Phòng Kem      |
| BMN  | Bánh Mì Ngọt   |
| BAP  | Bánh Ăn Phụ    |

### production_group — Nhóm sản xuất (planning dimension)

Dùng để gom các sản phẩm vào cùng 1 kế hoạch SX (FREE_GROUP hoặc BATCH_FORMULA).
Mỗi production_group thuộc về 1 item_group.

**Hai chiều hoàn toàn độc lập:**

```
Bánh Bắp Size 12
  → item_group     = PL (Phòng Lạnh)        ← thuộc phòng nào
  → production_group = Bánh Bắp Group        ← sản xuất cùng lô với size khác

Pana Cotta xoài
  → item_group     = PK (Phòng Kem)          ← thuộc phòng nào
  → production_group = Pana Cotta Group      ← sản xuất cùng target với sốt khác

Bánh Tiramisu
  → item_group     = PK (Phòng Kem)
  → production_group = null                  ← SIMPLE, không thuộc group
```

### Filter logic trên UI

| Filter            | Kết quả                                                          |
|-------------------|------------------------------------------------------------------|
| item_group = PL   | SIMPLE items có PL + production_groups có PL (Bánh Bắp Group)   |
| item_group = PK   | SIMPLE items có PK + production_groups có PK (Pana Cotta Group)  |
| (không filter)    | Tất cả                                                           |

---

## 4. Database Schema (các bảng mới)

### `item_group`

```sql
CREATE TABLE item_group (
    id         UUID PRIMARY KEY,
    code       VARCHAR(20)  UNIQUE NOT NULL,
    name       VARCHAR(100) NOT NULL,
    sort_order INT DEFAULT 0
);
```

### `production_threshold_rule` (Pattern 1 — SIMPLE)

```sql
CREATE TABLE production_threshold_rule (
    id               UUID PRIMARY KEY,
    item_id          UUID NOT NULL REFERENCES item(id),
    day_type         VARCHAR(10) NOT NULL,     -- WEEKDAY | WEEKEND
    sort_order       INT  NOT NULL DEFAULT 1,
    condition_type   VARCHAR(10) NOT NULL,     -- COUNT | PERCENT
    condition_value  NUMERIC(10,2) NOT NULL,   -- ngưỡng (số lượng hoặc %)
    produce_qty      INT NOT NULL,             -- số lượng cần làm thêm
    UNIQUE (item_id, day_type, sort_order)
);
```

### `production_group` (Pattern 2 & 3)

```sql
CREATE TABLE production_group (
    id                  UUID PRIMARY KEY,
    code                VARCHAR(50) UNIQUE NOT NULL,
    name                VARCHAR(200) NOT NULL,
    group_type          VARCHAR(20) NOT NULL,    -- FREE_GROUP | BATCH_FORMULA
    item_group_id       UUID REFERENCES item_group(id),
    -- FREE_GROUP fields
    target_weekday      INT,                     -- tổng cần làm ngày thường
    target_weekend      INT,                     -- tổng cần làm cuối tuần
    -- BATCH_FORMULA fields
    batch_weight_grams  INT,                     -- trọng lượng 1 cối (gram)
    note                TEXT
);
```

### `production_group_item` (items trong group)

```sql
CREATE TABLE production_group_item (
    id              UUID PRIMARY KEY,
    group_id        UUID NOT NULL REFERENCES production_group(id),
    item_id         UUID NOT NULL REFERENCES item(id),
    grams_per_unit  NUMERIC(8,2),   -- gram/cái (chỉ dùng cho BATCH_FORMULA)
    sort_order      INT DEFAULT 0,
    UNIQUE (group_id, item_id)
);
```

### Thêm vào bảng `item` (existing)

```sql
ALTER TABLE item ADD COLUMN item_group_id UUID REFERENCES item_group(id);
```

---

## 5. API Endpoints

### 5.1 Master Data — Item Group

| Method | Path                        | Mô tả                    |
|--------|-----------------------------|--------------------------|
| GET    | /api/v1/item-groups         | Lấy danh sách item_group |
| POST   | /api/v1/item-groups         | Tạo mới                  |
| PUT    | /api/v1/item-groups/{id}    | Cập nhật                 |
| DELETE | /api/v1/item-groups/{id}    | Xóa                      |

### 5.2 Master Data — Production Group

| Method | Path                              | Mô tả                          |
|--------|-----------------------------------|--------------------------------|
| GET    | /api/v1/production-groups         | Danh sách (filter by itemGroup)|
| GET    | /api/v1/production-groups/{id}    | Chi tiết + items trong group   |
| POST   | /api/v1/production-groups         | Tạo group                      |
| PUT    | /api/v1/production-groups/{id}    | Cập nhật group                 |
| POST   | /api/v1/production-groups/{id}/items | Thêm item vào group        |
| DELETE | /api/v1/production-groups/{id}/items/{itemId} | Xóa item khỏi group |

### 5.3 Master Data — Threshold Rules (Pattern 1)

| Method | Path                                    | Mô tả                     |
|--------|-----------------------------------------|---------------------------|
| GET    | /api/v1/items/{itemId}/threshold-rules  | Rules của 1 sản phẩm      |
| POST   | /api/v1/items/{itemId}/threshold-rules  | Thêm rule                 |
| PUT    | /api/v1/threshold-rules/{ruleId}        | Cập nhật rule             |
| DELETE | /api/v1/threshold-rules/{ruleId}        | Xóa rule                  |

### 5.4 Production Planning

| Method | Path                                         | Role      | Mô tả                                              |
|--------|----------------------------------------------|-----------|----------------------------------------------------|
| GET    | /api/v1/production/plans?date={date}         | Manager   | Xem kế hoạch của 1 ngày (DRAFT hoặc APPROVED)     |
| GET    | /api/v1/production/plans/{id}                | All       | Chi tiết kế hoạch (bếp dùng endpoint này)         |
| PUT    | /api/v1/production/plans/{id}                | Manager   | Chỉnh sửa số lượng khi còn DRAFT                  |
| POST   | /api/v1/production/plans/{id}/approve        | Manager   | Approve → bếp thấy, không sửa được nữa            |
| POST   | /api/v1/production/plans/{id}/reject         | Manager   | Reject → tạo lại DRAFT mới                        |
| GET    | /api/v1/production/plans?status=APPROVED     | Bếp       | Bếp xem danh sách kế hoạch đã approved            |

> Kế hoạch được **tự động tạo** khi gọi `POST /api/v1/daily-reports/{id}/finalize`.
> Không có endpoint tạo kế hoạch thủ công.

---

## 6. Request/Response Mẫu

### GET /api/v1/production/plans?date=2026-07-14 (Manager xem DRAFT)

**Response:**
```json
{
  "planDate": "2026-07-14",
  "dayType": "WEEKDAY",
  "suggestions": [
    {
      "type": "SIMPLE",
      "itemId": "uuid-tiramisu",
      "itemCode": "TIRAMISU-01",
      "itemName": "Bánh Tiramisu",
      "itemGroup": "PK",
      "qtyRemaining": 8,
      "suggestedQty": 12,
      "ruleMatched": { "conditionValue": 12, "produceQty": 12 },
      "note": "Còn 8, ngưỡng <12 → làm thêm 12"
    },
    {
      "type": "FREE_GROUP",
      "groupId": "uuid-pana-group",
      "groupCode": "PANA_COTTA",
      "groupName": "Pana Cotta Group",
      "itemGroup": "PK",
      "totalRemaining": 16,
      "totalTarget": 40,
      "suggestedTotal": 24,
      "items": [
        { "itemId": "uuid-pana-xoai", "itemName": "Pana Cotta Xoài", "qtyRemaining": 8, "suggestedQty": null },
        { "itemId": "uuid-pana-chanh", "itemName": "Pana Cotta Chanh Dây", "qtyRemaining": 5, "suggestedQty": null },
        { "itemId": "uuid-pana-dau", "itemName": "Pana Cotta Dâu", "qtyRemaining": 3, "suggestedQty": null }
      ],
      "note": "Tổng còn 16, target 40 → làm thêm 24 (nhân viên tự phân bổ)"
    },
    {
      "type": "BATCH_FORMULA",
      "groupId": "uuid-bap-group",
      "groupCode": "BANH_BAP",
      "groupName": "Bánh Bắp Group",
      "itemGroup": "PL",
      "batchWeightGrams": 10000,
      "suggestedBatches": 2,
      "items": [
        {
          "itemId": "uuid-bap-12", "itemName": "Bánh Bắp Size 12",
          "gramsPerUnit": 100, "qtyRemaining": 5,
          "suggestedQty": 30
        },
        {
          "itemId": "uuid-bap-14", "itemName": "Bánh Bắp Size 14",
          "gramsPerUnit": 150, "qtyRemaining": 3,
          "suggestedQty": 20
        },
        {
          "itemId": "uuid-bap-18", "itemName": "Bánh Bắp Size 18",
          "gramsPerUnit": 200, "qtyRemaining": 2,
          "suggestedQty": 10
        },
        {
          "itemId": "uuid-bap-20", "itemName": "Bánh Bắp Size 20",
          "gramsPerUnit": 300, "qtyRemaining": 1,
          "suggestedQty": 6
        }
      ],
      "note": "2 cối × 10kg. Nhân viên có thể điều chỉnh phân bổ size, tổng gram phải = N × 10,000g"
    }
  ]
}
```

---

### PUT /api/v1/production/plans/{id} (Manager điều chỉnh, chỉ khi DRAFT)

```json
{
  "lines": [
    {
      "type": "SIMPLE",
      "itemId": "uuid-tiramisu",
      "adjustedQty": 12
    },
    {
      "type": "FREE_GROUP",
      "groupId": "uuid-pana-group",
      "items": [
        { "itemId": "uuid-pana-xoai",  "adjustedQty": 10 },
        { "itemId": "uuid-pana-chanh", "adjustedQty": 8  },
        { "itemId": "uuid-pana-dau",   "adjustedQty": 6  }
      ]
    },
    {
      "type": "BATCH_FORMULA",
      "groupId": "uuid-bap-group",
      "items": [
        { "itemId": "uuid-bap-12", "adjustedQty": 36 },
        { "itemId": "uuid-bap-14", "adjustedQty": 18 },
        { "itemId": "uuid-bap-18", "adjustedQty": 8  },
        { "itemId": "uuid-bap-20", "adjustedQty": 4  }
      ]
    }
  ]
}
```

### POST /api/v1/production/plans/{id}/approve (Manager approve)

```json
// No body needed — server records approvedBy từ JWT token
```

**Response:** ProductionPlan với status = APPROVED. Bếp có thể thấy ngay sau đó.
```

---

## 7. Validation phía FE

### Pattern 2 — FREE_GROUP

```
∑(confirmedQty của tất cả items trong group) == suggestedTotal
```
> Nếu không bằng → warning nhưng vẫn cho confirm (nhân viên có thể thay đổi target)

### Pattern 3 — BATCH_FORMULA

```
∑(confirmedQty[i] × gramsPerUnit[i]) % batchWeightGrams == 0
```
> **Hard validation** — phải đúng mới được confirm. Hiển thị lỗi: "Tổng trọng lượng phải là bội số của 10,000g (1 cối)"

---

## 8. Màn hình UI đề xuất

### 8.1 Màn hình Manager Review (DRAFT — chỉ Chính thấy)

```
┌─────────────────────────────────────────────────────────┐
│  KẾ HOẠCH SẢN XUẤT — Thứ 3, 14/07/2026  [DRAFT]       │
│                                                          │
│  Filter: [Tất cả ▼]  [Phòng Lạnh] [Phòng Kem] [BMN]   │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  📦 PHÒNG KEM                                            │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Bánh Tiramisu        Còn: 8    Gợi ý: +12      │    │
│  │ Bánh Mousse Socola   Còn: 15   Gợi ý: —        │    │
│  │                                                  │    │
│  │ 🔲 Pana Cotta Group              Làm thêm: 24   │    │
│  │   └ xoài       còn 8   [ 10 ] cái              │    │
│  │   └ chanh dây  còn 5   [  8 ] cái              │    │
│  │   └ dâu        còn 3   [  6 ] cái              │    │
│  │                         Tổng: 24 ✓             │    │
│  └─────────────────────────────────────────────────┘    │
│                                                          │
│  📦 PHÒNG LẠNH                                           │
│  ┌─────────────────────────────────────────────────┐    │
│  │ 🔲 Bánh Bắp Group        2 cối (20kg)           │    │
│  │   └ Size 12  còn 5   [ 36 ] cái  (3,600g)     │    │
│  │   └ Size 14  còn 3   [ 18 ] cái  (2,700g)     │    │
│  │   └ Size 18  còn 2   [  8 ] cái  (1,600g)     │    │
│  │   └ Size 20  còn 1   [  4 ] cái  (1,200g) ⚠   │    │
│  │              Tổng gram: 9,100 / 20,000g ❌      │    │
│  └─────────────────────────────────────────────────┘    │
│                                                          │
│                      [APPROVE KẾ HOẠCH]                 │
└─────────────────────────────────────────────────────────┘

### 8.2 Màn hình Bếp (chỉ thấy sau khi APPROVED)

```
┌─────────────────────────────────────────────────────────┐
│  KẾ HOẠCH SẢN XUẤT — Thứ 3, 14/07/2026  [APPROVED]    │
│  Approved by: Chính — 22:15                             │
├─────────────────────────────────────────────────────────┤
│  📦 PHÒNG KEM                                            │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Bánh Tiramisu          Cần làm: 12             │    │
│  │                                                  │    │
│  │ Pana Cotta Group       Cần làm: 24 tổng cộng   │    │
│  │   └ xoài:       10                              │    │
│  │   └ chanh dây:   8                              │    │
│  │   └ dâu:         6                              │    │
│  └─────────────────────────────────────────────────┘    │
│                                                          │
│  📦 PHÒNG LẠNH                                           │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Bánh Bắp Group         2 cối                    │    │
│  │   └ Size 12: 36 cái                             │    │
│  │   └ Size 14: 18 cái                             │    │
│  │   └ Size 18:  8 cái                             │    │
│  │   └ Size 20:  4 cái                             │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```
> Bếp chỉ xem, không chỉnh sửa được.
```

---

## 9. Enum Values tham khảo

### DayType
| Value   | Mô tả              |
|---------|--------------------|
| WEEKDAY | Thứ 2 — Thứ 6      |
| WEEKEND | Thứ 7 + Chủ nhật   |

### GroupType
| Value         | Mô tả                                          |
|---------------|------------------------------------------------|
| FREE_GROUP    | Nhóm tự do — nhân viên tự phân bổ từng loại   |
| BATCH_FORMULA | Lô cố định theo trọng lượng                    |

### ConditionType (Threshold Rules)
| Value   | Mô tả                                    |
|---------|------------------------------------------|
| COUNT   | Số lượng tuyệt đối (còn lại < X cái)    |
| PERCENT | Phần trăm so với target (còn lại < X%)  |

---

## 10. Open Questions / Chưa xác định

| # | Câu hỏi                                                                            | Status              |
|---|------------------------------------------------------------------------------------|---------------------|
| 1 | Tồn kho cuối ngày lấy từ `DailyReport.qtyRemainingActual` — đã xác nhận?          | ✅ Đã đồng ý         |
| 2 | FREE_GROUP: nếu tổng ≠ suggestedTotal, có cảnh báo hay chặn?                      | ⚠ Warning, không chặn |
| 3 | Kế hoạch SX có approval flow DRAFT → APPROVED?                                     | ✅ Có — Chính approve |
| 4 | File export cho bếp?                                                                | ✅ Không export — trên hệ thống |
| 5 | Một sản phẩm có thể vừa là SIMPLE vừa thuộc FREE_GROUP không?                      | ❌ Không             |
| 6 | Khi DailyReport finalize → tự tạo plan DRAFT hay cần trigger thủ công?             | ✅ Tự động khi finalize |

---

*Tài liệu này sẽ được cập nhật khi có thêm quyết định thiết kế.*
