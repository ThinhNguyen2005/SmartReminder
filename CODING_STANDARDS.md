# Cue Android Coding Standards & Architecture Guidelines

> **Highest Priority Project Rule:** Toàn bộ code trong dự án **Cue (SmartReminder)** bắt buộc phải tuân thủ nghiêm ngặt các quy chuẩn dưới đây. Không có ngoại lệ.

---

## 1. 🎨 Design System & UI Tokens (Tuyệt đối không Hardcode)

### 1.1. Color Tokens (`Color.kt`)
* **CẤM:** Không viết mã màu Hex trực tiếp trong Composable (VD: `Color(0xFF18181B)` ❌).
* **BẮT BUỘC:** Sử dụng các token màu đã định nghĩa trong [`Color.kt`](file:///d:/SmartReminder/app/src/main/java/com/smartreminder/ui/theme/Color.kt) hoặc `MaterialTheme.colorScheme`:
  - `CueBackground` (`#FAFAF9`): Nền ấm, chống mỏi mắt.
  - `CueSurface` (`#FFFFFF`), `CueSurfaceSubtle` (`#F4F4F5`): Nền component và row.
  - `CueTextPrimary` (`#18181B`), `CueTextSecondary` (`#71717A`), `CueTextTertiary` (`#A1A1AA`).
  - `CueAccent` (`#4F46E5`), `CueAccentStrong` (`#4338CA`), `CueAccentContainer` (`#EEF2FF`): Điểm nhấn Intelligence/Selected.
  - `CueCta` (`#18181B`), `CueOnCta` (`#FFFFFF`): Nút hành động chính.
  - `CueBorder` (`#E4E4E7`), `CueBorderStrong` (`#D4D4D8`).
* **Tỷ lệ 80 / 15 / 5:** 80% Neutral, 15% Indigo (chọn/thông minh), 5% Semantic (cực kỳ tiết chế).

### 1.2. Spacing Scale (`Spacing.kt`)
* **CẤM:** Không tự ý dùng các giá trị dp lẻ (VD: `7.dp`, `14.dp`, `22.dp` ❌).
* **BẮT BUỘC:** 100% padding, margin, spacer, corner radius phải sử dụng [`CueSpacing`](file:///d:/SmartReminder/app/src/main/java/com/smartreminder/ui/theme/Spacing.kt):
  - `CueSpacing.Xs` = `4.dp`
  - `CueSpacing.Sm` = `8.dp` (Semantic spacing: Title $\rightarrow$ Subtitle)
  - `CueSpacing.Md` = `12.dp`
  - `CueSpacing.Lg` = `16.dp` (Component padding, button-to-button)
  - `CueSpacing.Xl` = `24.dp` (Screen horizontal padding, Subtitle $\rightarrow$ Section)
  - `CueSpacing.Xxl` = `32.dp`
  - `CueSpacing.Xxxl` = `48.dp`

### 1.3. Typography Scale (`Type.kt`)
* **CẤM:** Không dùng `.copy(fontSize = ...)` trực tiếp trên Composable (VD: `MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)` ❌).
* **BẮT BUỘC:** Sử dụng trực tiếp các token chuẩn từ `MaterialTheme.typography`:
  - `headlineLarge`: `32sp / 600 / 40sp` (Tiêu đề chính mỗi màn hình)
  - `titleLarge`: `20sp / 600 / 28sp` (Tiêu đề BottomSheet/Dialog)
  - `titleMedium`: `16sp / 600 / 22sp` (Tên task, tên mục lựa chọn)
  - `bodyLarge`: `16sp / 400 / 24sp` (Phụ đề, đoạn văn mô tả)
  - `bodyMedium`: `14sp / 400 / 20sp` (Mô tả chi tiết dưới mục chọn)
  - `bodySmall`: `12sp / 400 / 16sp` (Ghi chú nhỏ, reasoning tooltip)
  - `labelLarge`: `14sp / 500 / 20sp` (Nút bấm, Time pill)
  - `labelSmall`: `12sp / 600 / 16sp` (Section Eyebrow, AI tag)

---

## 2. 🌐 Internationalization & Chuẩn hóa String (`i18n`)

* **CẤM:** Không hardcode bất kỳ chuỗi text nào trong code Kotlin (VD: `Text("Continue")` ❌).
* **BẮT BUỘC:** 100% text phải dùng `stringResource(R.string.key)` và khai báo song ngữ:
  - File tiếng Anh mặc định: [`app/src/main/res/values/strings.xml`](file:///d:/SmartReminder/app/src/main/res/values/strings.xml)
  - File tiếng Việt: [`app/src/main/res/values-vi/strings.xml`](file:///d:/SmartReminder/app/src/main/res/values-vi/strings.xml)
* **Quy tắc đặt tên String Key (Scope-Feature):**
  - Mẫu chung: `[screen]_[section]_[element]`
  - Ví dụ: `onboarding_rhythm_title`, `onboarding_rhythm_wake_up`, `timeline_ai_tooltip_desc`, `action_continue`, `action_cancel`.
* **Biến động:** Luôn dùng placeholder định dạng chuẩn: `%1$d`, `%1$s` (VD: `stringResource(R.string.onboarding_goals_badge, count)`).

---

## 3. 📱 Jetpack Compose & Mobile Architecture

### 3.1. Phân định Trách nhiệm & State Hoisting (UDF)
* Màn hình (Screen Composable) chỉ nhận `uiState: SomeUiState` và phát ra các event lambdas `onAction: () -> Unit`.
* Không gọi API, không query database và không lưu trữ business logic phức tạp bên trong Composable.

### 3.2. Cấu trúc Khung màn hình (Screen Skeleton)
* **Top Bar:** Luôn đặt trong `Scaffold(topBar = { ... })`.
* **Bottom Bar (CTA):** Luôn đặt trong `Scaffold(bottomBar = { ... })` kèm `navigationBarsPadding()`.
* **Content:** Chảy tự nhiên từ trên xuống dưới (Top-aligned), luôn bọc `.verticalScroll(rememberScrollState())` để an toàn trước font scaling 150%–200% và màn hình nhỏ.
* **CẤM:** Không dùng `Arrangement.SpaceBetween` hay `justify-center` làm trôi nổi nội dung giữa màn hình.

### 3.3. Công thái học Di động (Mobile Ergonomics & Fitts's Law)
* **Touch Target:** Kích thước vùng bấm tối thiểu $\ge 48\text{dp}$.
* **Whole-Row Clickable:** Đối với các hàng danh sách/cài đặt, đặt `clickable` trên toàn bộ container `Row`.
* **CẤM:** Không lồng các click target vào nhau (Nested clickables).
* **Xóa thừa:** `Surface(shape = ...)` đã tự bo góc, không thêm `.clip()` trùng lặp.

### 3.4. Hỗ trợ Tiếp cận (TalkBack Semantics)
* Khi một hàng có icon + title + value, dùng `.semantics(mergeDescendants = true) { contentDescription = "$title, $value" }` để TalkBack đọc liền mạch và có nghĩa.

### 3.5. Bắt buộc có `@Preview`
* Mỗi Screen và Custom Component lớn bắt buộc phải có hàm `@Preview` bọc trong `SmartReminderTheme`.

---

## 4. 🤖 Triết lý Sản phẩm & AI UX (Product Integrity)

* **Không Overclaiming:** Không tự nhận "AI biết chính xác năng lượng của bạn" chỉ từ giờ thức/ngủ.
* **Đúng bản chất:** 
  - `Awake Window` = Khoảng thời gian thức (dùng làm boundary tránh xếp task khi đang ngủ).
  - `Free Slots` = Thời gian thức − Lịch cố định − Thời gian cá nhân − Buffer.
* **Minh bạch & Kiểm soát:** Mọi đề xuất AI đều phải có lý do ngắn gọn khi người dùng chạm vào. Người dùng luôn là người quyết định cuối cùng (*"Cue suggests. You stay in control."*).

---

## 5. 🧪 Chiến lược Kiểm thử (Testing Strategy) & Clean Code

### 5.1. Khi nào BẮT BUỘC viết Unit Test?
> **Quy tắc vàng:** *"Nếu bạn có thể viết câu 'Given X, when Y, then Z' mà không cần mở emulator → bắt buộc phải có Unit Test."*
> *"UI có thể đổi, nhưng business logic sai là app hỏng."*

* **Các thành phần bắt buộc có Unit Test (Domain Layer 80–95% coverage):**
  - **Tính toán thời gian:** Qua đêm (`10:00 → 02:00`), cùng mốc (`08:00 → 08:00`), chuyển múi giờ.
  - **Schedule Engines:** `FreeSlotCalculator` (tính khoảng trống), `ConflictDetector` (phát hiện xung đột lịch).
  - **Reminder Engines:** Tính mốc nhắc nhở trước 30p / 3h / 1 ngày, Snooze logic.
  - **Routine & Rules:** Lặp lại theo `Mon/Wed/Fri`, `Skip Today`, `Run Today`.
  - **Prioritization & AI Validation:** Trọng số ưu tiên, kiểm duyệt đề xuất hợp lệ.
  - **ViewModel:** State transitions cho các luồng nghiệp vụ quan trọng.

### 5.2. Khi nào KHÔNG cần Unit Test?
* Các đoạn chỉ render UI thuần túy: `Text`, `Icon`, `Spacer`, `.padding()`, `RoundedCornerShape()`... (để dành cho Manual Review hoặc Compose UI Test khi cần).

### 5.3. Quy trình TDD khi viết Logic
1. Xác định Expected Behavior (Given - When - Then).
2. Viết Test case $\rightarrow$ Chạy báo đỏ (Fail).
3. Viết code logic tối thiểu $\rightarrow$ Chạy báo xanh (Pass).
4. Refactor clean code.

### 5.4. Pre-flight Verification Checklist
Trước khi hoàn tất bất kỳ tính năng nào:
1. `.\gradlew.bat compileDebugSources` $\rightarrow$ Phải đạt **BUILD SUCCESSFUL (0 errors)**.
2. `.\gradlew.bat test` $\rightarrow$ Toàn bộ Unit Tests phải pass (100%).
3. Không để lại import thừa, không dead code, không TODO chưa giải quyết.
