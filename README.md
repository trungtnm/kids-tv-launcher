# Kids TV Launcher

Màn hình chính gọn nhẹ kèm parental control cho Android TV box đặt ở phòng trẻ em.
Ứng dụng chỉ hiện những ứng dụng ba mẹ cho phép, khóa mọi thứ còn lại bằng mã PIN,
giới hạn thời gian xem, và cho ba mẹ điều khiển từ điện thoại qua trình duyệt.

Không cần root và không cần máy chủ bên ngoài. Mọi thứ chạy ngay trên box.

## Tính năng

**Trên TV**

- Màn hình chính tối giản, chỉ hiện ứng dụng của hồ sơ đang xem.
- Nhiều hồ sơ trẻ em, mỗi hồ sơ có danh sách ứng dụng, hạn mức phút mỗi ngày và giờ đi ngủ riêng.
- Ứng dụng ngoài danh sách (kể cả Cài đặt và Play Store) bị che bằng màn hình khóa, cần mã PIN của ba mẹ.
- Chế độ ba mẹ: nhập PIN để mở khóa mọi thứ trong 15 phút, hoặc đến khi TV tắt màn hình.

**Trên điện thoại** (trang web do box tự phát, mở bằng Safari hoặc trình duyệt bất kỳ)

- Tab Điều khiển: tạm dừng TV, cộng thêm giờ, tắt ứng dụng đang mở, mở nhanh ứng dụng,
  phát một link YouTube, đổi hồ sơ, hẹn giờ tắt, bật lại TV.
- Tab Quản lý: sửa hồ sơ, chọn ứng dụng được phép, đổi mã PIN.
- Tab Báo cáo: thời gian xem hôm nay theo ứng dụng và biểu đồ 7 ngày.

## Cách hoạt động

| Thành phần | File | Vai trò |
|---|---|---|
| Màn hình chính | `LauncherActivity.kt`, `Ui.kt` | Lưới ứng dụng, chọn hồ sơ, trạng thái khóa |
| Màn hình khóa | `LockActivity.kt` | Thông báo lý do khóa, nhập và tạo mã PIN |
| Dịch vụ khóa | `GuardService.kt` | Accessibility service theo dõi ứng dụng đang mở, chặn, tính giờ, hẹn giờ tắt |
| Chính sách | `Store.kt` | Hồ sơ, mã PIN (băm SHA-256 có salt), thời gian đã xem, quyết định cho phép hay chặn |
| Trang quản lý | `WebServer.kt`, `assets/web/index.html` | Máy chủ HTTP cổng 8765 và giao diện web |

Các file Kotlin nằm trong `app/src/main/java/com/kidstv/launcher/`.

Dịch vụ khóa là accessibility service vì đó là cách duy nhất để một ứng dụng thường biết
ứng dụng nào đang ở phía trước và che nó lại. Hệ thống cũng tự khởi động lại dịch vụ này
sau khi box khởi động lại, nên trang quản lý chạy chung trong đó.

## Yêu cầu

- Android TV box chạy Android 9 trở lên (đã dùng trên Android TV 12), bật gỡ lỗi qua mạng (ADB).
- Máy tính có JDK 17, Android SDK (platform 34, build-tools 34.0.0) và `adb`.

Trên macOS với Homebrew:

```sh
brew install openjdk@17 android-platform-tools
brew install --cask android-commandlinetools
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

## Build

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties
./gradlew assembleRelease
```

File cài đặt nằm ở `app/build/outputs/apk/release/app-release.apk`. Bản release được ký bằng
khóa debug của máy build, nên các bản cập nhật phải được build từ cùng một máy (hoặc cùng
`~/.android/debug.keystore`).

## Cài lên box

Thay `<BOX_IP>` bằng địa chỉ của box trong mạng nhà.

```sh
adb connect <BOX_IP>:5555          # lần đầu phải bấm cho phép trên TV
adb install -r app/build/outputs/apk/release/app-release.apk

# Bật dịch vụ khóa
adb shell settings put secure enabled_accessibility_services \
  com.kidstv.launcher/com.kidstv.launcher.GuardService
adb shell settings put secure accessibility_enabled 1

# Đặt làm màn hình chính. Nhiều firmware Android TV vẫn ép nút Home về launcher gốc,
# nên phải tắt launcher gốc đi.
adb shell cmd package set-home-activity com.kidstv.launcher/.LauncherActivity
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell input keyevent KEYCODE_HOME
```

Lần mở đầu tiên, TV yêu cầu tạo mã PIN cho ba mẹ. Sau đó mở `http://<BOX_IP>:8765` trên
điện thoại, đăng nhập bằng mã PIN và chọn ứng dụng cho từng hồ sơ. Trên iPhone có thể
bấm Chia sẻ → "Thêm vào MH chính" để dùng như một ứng dụng.

Cập nhật bản mới chỉ cần chạy lại `adb install -r`. Dữ liệu hồ sơ, mã PIN và thời gian
đã xem được giữ nguyên.

### Thiết lập nên làm thêm trên box

```sh
# Bộ lọc DNS gia đình của Cloudflare, chặn trang người lớn trong mọi ứng dụng
adb shell settings put global private_dns_specifier family.cloudflare-dns.com
adb shell settings put global private_dns_mode hostname
```

Nên gỡ trình duyệt, trình tải file và các kho ứng dụng ngoài khỏi box. Hãy sao lưu APK
bằng `adb pull` trước khi gỡ.

## Khôi phục về trạng thái cũ

```sh
adb shell pm enable com.google.android.tvlauncher
adb shell settings put secure enabled_accessibility_services '""'
adb uninstall com.kidstv.launcher
adb shell settings put global private_dns_mode off
```

Nếu quên mã PIN, gỡ ứng dụng rồi cài lại sẽ xóa mã PIN cùng toàn bộ hồ sơ.

## API của trang quản lý

Mọi lệnh trừ `status` và `login` cần header `X-Token` nhận được khi đăng nhập.

| Phương thức | Đường dẫn | Nội dung |
|---|---|---|
| GET | `/api/status` | Box đã có mã PIN chưa, thời gian khóa do nhập sai |
| POST | `/api/login` | `{"pin": "..."}`, trả về token |
| GET | `/api/state` | Hồ sơ, trạng thái khóa, ứng dụng đang mở, thời gian đã xem |
| POST | `/api/policy` | Lưu `profiles` và `switchLocked` |
| POST | `/api/action` | `{"type": ...}`, xem bảng dưới |

| `type` | Tham số | Tác dụng |
|---|---|---|
| `pause`, `resume` | | Tạm dừng hoặc cho xem tiếp |
| `extra` | `minutes` | Bỏ qua hạn mức và giờ ngủ trong số phút đó; 0 để hủy |
| `profile` | `id` | Đổi hồ sơ đang xem |
| `lock` | | Kết thúc chế độ ba mẹ |
| `home`, `close`, `wake` | | Về màn hình chính, tắt ứng dụng đang mở, bật TV |
| `launch` | `pkg` | Mở một ứng dụng |
| `youtube` | `url` | Phát một video trong ứng dụng YouTube |
| `sleep` | `minutes` | Hẹn giờ tắt; 0 để hủy |
| `pin` | `current`, `pin` | Đổi mã PIN, cần mã hiện tại |

## Giới hạn

- Trang quản lý dùng HTTP không mã hóa và chỉ truy cập được trong mạng nhà. Mã PIN đi
  dạng thô trong mạng nội bộ, nên đừng mở cổng 8765 ra Internet.
- Đây là rào chắn cho trẻ nhỏ, không phải bảo mật tuyệt đối. Khôi phục cài đặt gốc bằng
  nút cứng trên box sẽ xóa toàn bộ thiết lập.
- Ứng dụng không dùng Device Owner, vì nhiều firmware Android TV không khai báo tính năng
  `device_admin`. Mọi việc khóa đều dựa vào accessibility service.
- "Bật TV" và hẹn giờ tắt đưa box vào và ra khỏi chế độ ngủ. Chiếc TV có tự tắt và bật
  theo hay không tùy vào thiết lập HDMI-CEC.
- Ứng dụng mở video theo link YouTube qua gói `com.google.android.youtube.tv`, nên cần
  bản YouTube chính thức cho Android TV.
- Trên một số firmware, `adb root` làm dịch vụ ADB ngừng hoạt động cho đến khi khởi động
  lại box. Ứng dụng không cần root, nên đừng chạy lệnh đó.
