# Hướng dẫn phát triển

Tài liệu cho người muốn dựng, sửa hoặc mở rộng Kids TV Launcher. Hướng dẫn sử dụng cho
ba mẹ nằm trong [README.md](README.md).

## Tổng quan

Một ứng dụng Android duy nhất (`com.kidstv.launcher`), viết bằng Kotlin và Jetpack
Compose, không có backend. minSdk 28, targetSdk 34.

| Thành phần | File | Vai trò |
|---|---|---|
| Màn hình chính | `LauncherActivity.kt` | Lưới ứng dụng, chọn hồ sơ, trạng thái khóa theo giờ |
| Màn hình khóa | `LockActivity.kt` | Thông báo lý do khóa, nhập và tạo mã PIN, cho thêm giờ |
| Thành phần giao diện | `Ui.kt` | Nền, thẻ có hiệu ứng focus, nút, bàn phím số |
| Dịch vụ khóa | `GuardService.kt` | Accessibility service: theo dõi ứng dụng phía trước, chặn, tính giờ, hẹn giờ tắt, đánh thức |
| Chính sách | `Store.kt` | Hồ sơ, mã PIN, phiên đăng nhập, thời gian đã xem, hàm quyết định `decide()` |
| Danh sách ứng dụng | `Apps.kt` | Liệt kê và mở ứng dụng, lấy banner và icon |
| Máy chủ web | `WebServer.kt` | NanoHTTPD cổng 8765, API JSON, phát trang quản lý |
| Trang quản lý | `assets/web/index.html` | Một file HTML với JavaScript thuần, không có bước build |

Các file Kotlin nằm trong `app/src/main/java/com/kidstv/launcher/`.

### Luồng chặn

1. Hệ thống gửi sự kiện `TYPE_WINDOW_STATE_CHANGED` cho `GuardService` mỗi khi một cửa sổ mới lên phía trước.
2. Dịch vụ bỏ qua các bề mặt hệ thống (`Store.isSystemSurface`), ghi lại gói đang mở vào `Store.currentPkg`, rồi gọi `Store.decide(pkg)`.
3. Nếu kết quả là `Verdict.Block`, dịch vụ mở `LockActivity` đè lên. Với khóa theo giờ (tạm dừng, giờ ngủ, hết hạn mức), ứng dụng bị chặn còn bị đóng để dừng phát.
4. Một bộ đếm 15 giây cộng thời gian xem cho hồ sơ đang dùng và kiểm tra lại chính sách. Các lệnh từ trang web gọi `recheck()` để áp dụng ngay, không chờ bộ đếm.

Thứ tự ưu tiên trong `decide()`: chế độ ba mẹ → tạm dừng → thêm giờ (`overrideUntil`) → giờ ngủ → hạn mức ngày → danh sách ứng dụng được phép.

### Lưu trữ

Hai file JSON trong thư mục `files` của ứng dụng, ghi theo kiểu ghi tạm rồi đổi tên:

- `policy.json`: salt và mã băm SHA‑256 của mã PIN, token đăng nhập, hồ sơ, hồ sơ đang dùng, cờ tạm dừng, `overrideUntil`, `sleepAt`.
- `usage.json`: số giây đã xem theo ngày, hồ sơ và gói; giữ 14 ngày gần nhất.

Chế độ ba mẹ chỉ nằm trong bộ nhớ và kết thúc sau 15 phút, khi màn hình tắt hoặc khi tiến trình khởi động lại.

## Yêu cầu

- JDK 17
- Android SDK: `platforms;android-34`, `build-tools;34.0.0`
- `adb`

Trên macOS với Homebrew:

```sh
brew install openjdk@17 android-platform-tools
brew install --cask android-commandlinetools
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

## Dựng

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties
./gradlew assembleRelease
```

Kết quả nằm ở `app/build/outputs/apk/release/app-release.apk` (khoảng 1,6 MB, đã bật R8).

Bản release được ký bằng khóa debug của máy dựng. Điều này đủ cho việc cài tay lên thiết bị
trong nhà, nhưng mọi bản cập nhật phải được ký bằng cùng `~/.android/debug.keystore`, nếu
không `adb install -r` sẽ từ chối và phải gỡ ứng dụng (mất dữ liệu).

## Cài và cập nhật trên box

```sh
adb connect <BOX_IP>:5555
adb install -r app/build/outputs/apk/release/app-release.apk
```

Thiết lập lần đầu (bật accessibility service, tắt launcher gốc) được mô tả trong
[README.md](README.md#cài-đặt). Sau `adb install -r`, hệ thống tự gắn lại accessibility
service trong vài giây; kiểm tra bằng:

```sh
adb shell dumpsys accessibility | grep "Bound services"
```

Hai điều cần biết khi cài lại:

- Tiến trình khởi động lại nên chế độ ba mẹ đang bật sẽ kết thúc.
- Thời gian xem chưa kịp ghi xuống đĩa (tối đa khoảng một phút) sẽ mất.

## Gỡ lỗi

Ứng dụng ghi nhật ký với thẻ `KidsTV` cho mỗi lệnh từ web, mỗi lần chặn và mỗi yêu cầu
đánh thức:

```sh
adb logcat -s KidsTV
```

Các lệnh hữu ích khác:

```sh
# Ứng dụng đang ở phía trước
adb shell dumpsys activity activities | grep mResumedActivity
# Box đang thức hay ngủ
adb shell dumpsys power | grep mWakefulness=
# Lý do đánh thức và đi ngủ gần đây
adb logcat -d | grep -E "Waking up|Going to sleep"
# Chụp màn hình TV
adb exec-out screencap -p > screen.png
```

Lớp video của các ứng dụng phát phim thường chụp ra màu đen. Để biết video có đang phát
không, dùng `adb shell dumpsys media_session`.

### Thử trang quản lý không cần box

`index.html` chỉ gọi `fetch` tới `/api/*`. Để xem giao diện trên máy tính, tạo một bản
sao của file và chèn trước thẻ `<script>` đầu tiên một đoạn đặt `localStorage.token`,
`localStorage.tab` và thay `window.fetch` bằng hàm trả về một đối tượng state mẫu. Ảnh
trong `docs/screenshots/web-*.png` được dựng theo cách này bằng Chrome headless:

```sh
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new \
  --force-device-scale-factor=2 --window-size=500,1190 \
  --screenshot=out.png file:///duong/dan/toi/ban-sao.html
```

Chrome headless không cho cửa sổ hẹp hơn 500 px; ảnh chụp ở chiều rộng nhỏ hơn sẽ bị cắt
bên phải chứ không phải do trang tràn ngang.

## API của trang quản lý

Mọi lệnh trừ `status` và `login` cần header `X-Token`. Thân yêu cầu là JSON mã hóa UTF‑8.

| Phương thức | Đường dẫn | Nội dung |
|---|---|---|
| GET | `/api/status` | `hasPin`, `lockoutSec` |
| POST | `/api/login` | `{"pin": "..."}`, trả về `token` |
| GET | `/api/state` | Hồ sơ, trạng thái khóa, ứng dụng đang mở, thời gian đã xem, danh sách ứng dụng |
| POST | `/api/policy` | Lưu `profiles` và `switchLocked`, trả về state |
| POST | `/api/action` | `{"type": ...}`, trả về state |
| GET | `/api/icon?pkg=…&token=…` | Icon PNG của một ứng dụng |

| `type` | Tham số | Tác dụng |
|---|---|---|
| `pause`, `resume` | | Tạm dừng hoặc cho xem tiếp |
| `extra` | `minutes` | Bỏ qua hạn mức và giờ ngủ trong số phút đó; 0 để hủy |
| `profile` | `id` | Đổi hồ sơ đang xem |
| `lock` | | Kết thúc chế độ ba mẹ |
| `home` | | Về màn hình chính |
| `close` | | Về màn hình chính và đóng ứng dụng đang mở |
| `wake` | | Đánh thức box |
| `launch` | `pkg` | Mở một ứng dụng nếu chính sách cho phép |
| `youtube` | `url` | Phát một video trong `com.google.android.youtube.tv` |
| `sleep` | `minutes` | Hẹn giờ tắt; 0 để hủy |
| `pin` | `current`, `pin` | Đổi mã PIN; thu hồi mọi token và trả về token mới |

Lỗi trả về dạng `{"error": "..."}` với mã 400, 401 (token sai, trang web sẽ đăng xuất),
403 (sai mã PIN hiện tại khi đổi PIN) hoặc 404. Nhập sai mã PIN 5 lần sẽ khóa 60 giây,
dùng chung giữa trang web và màn hình khóa trên TV.

## Những điều rút ra từ thiết bị thật

Các quyết định dưới đây đến từ việc thử trên một box Amlogic chạy Android TV 12. Hãy giữ
chúng khi sửa mã.

- **Không dùng Device Owner.** Firmware không khai báo `android.software.device_admin`, và
  hệ thống chỉ cho một người dùng nên hồ sơ hạn chế có sẵn của Android TV cũng không dùng
  được. Mọi việc khóa dựa vào accessibility service.
- **Phải tắt launcher gốc.** `cmd package set-home-activity` báo thành công nhưng nút Home
  vẫn về `com.google.android.tvlauncher` cho đến khi gói đó bị tắt.
- **Không gọi `GLOBAL_ACTION_HOME` trước khi mở màn hình khóa.** Hai thao tác chạy đua và
  màn hình chính có thể che mất màn hình khóa. `LockActivity` tự về màn hình chính khi đóng.
- **Đọc thân yêu cầu thành UTF‑8 bằng tay.** `parseBody` của NanoHTTPD giải mã theo
  US‑ASCII khi header không ghi charset, làm hỏng tiếng Việt. Xem `WebServer.body()`.
- **Đánh thức bằng `setTurnScreenOn`.** Wake lock với `ACQUIRE_CAUSES_WAKEUP` bị firmware bỏ
  qua. `GuardService.wake()` mở `LauncherActivity` kèm `EXTRA_WAKE`, và activity tự yêu cầu
  bật màn hình.
- **Tắt màn hình bằng `GLOBAL_ACTION_LOCK_SCREEN`.** Trên TV không có màn hình khóa, nên
  hành động này đưa box về chế độ ngủ. Box vẫn giữ Wi‑Fi và máy chủ web vẫn phản hồi khi ngủ.
- **Đừng chạy `adb root`.** Trên firmware này, lệnh đó làm dịch vụ ADB ngừng hoạt động cho
  đến khi khởi động lại box.
- **Chỉ đưa URL đã dựng lại vào intent.** `youtubeId()` trích mã video 11 ký tự và ghép lại
  thành link YouTube chuẩn, nên nội dung người dùng dán vào không bao giờ đi thẳng tới TV.

## Giới hạn đã biết

- Trang quản lý dùng HTTP không mã hóa; mã PIN và token đi dạng thô trong mạng nội bộ.
- Token đăng nhập không hết hạn; chúng chỉ bị thu hồi khi đổi mã PIN.
- Ứng dụng không lọc nội dung bên trong từng ứng dụng.
- Chưa có kiểm thử tự động. Việc kiểm thử hiện làm tay trên box qua `adb` và trang web.

## Quy ước

- Commit theo dạng conventional commits (`feat:`, `fix:`, `docs:`).
- Không commit `local.properties`, thư mục build, hay bản sao lưu lấy từ thiết bị
  (`device-backup/` đã nằm trong `.gitignore`).
