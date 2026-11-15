# Kids TV Launcher

**Tiếng Việt** | [English](README_EN.md)

Biến Android TV box trong phòng các bé thành một chiếc TV an toàn: bé chỉ thấy những
ứng dụng ba mẹ cho phép, xem đủ giờ thì TV tự khóa, và ba mẹ điều khiển mọi thứ từ
điện thoại.

Không cần root, không cần tài khoản hay dịch vụ bên ngoài. Mọi thứ chạy ngay trên box
và chỉ trong mạng Wi‑Fi nhà bạn.

## Ảnh màn hình

**Trên TV** (chụp từ box thật)

| Màn hình chính của một hồ sơ | Ứng dụng ngoài danh sách bị khóa |
|---|---|
| ![Màn hình chính](docs/screenshots/tv-home.jpg) | ![Ứng dụng bị khóa](docs/screenshots/tv-app-locked.jpg) |

| Đến giờ đi ngủ | Nhập mã PIN của ba mẹ |
|---|---|
| ![Giờ đi ngủ](docs/screenshots/tv-bedtime.jpg) | ![Nhập mã PIN](docs/screenshots/tv-pin.jpg) |

**Trên điện thoại** (trang quản lý dựng với dữ liệu mẫu)

| Điều khiển | Quản lý | Báo cáo |
|---|---|---|
| <img src="docs/screenshots/web-control.png" width="260" alt="Tab Điều khiển"> | <img src="docs/screenshots/web-manage.png" width="260" alt="Tab Quản lý"> | <img src="docs/screenshots/web-report.png" width="260" alt="Tab Báo cáo"> |

## Ứng dụng làm được gì

**Cho các bé**

- Màn hình chính đơn giản, chỉ có các ứng dụng của bé, không quảng cáo và không gợi ý nội dung.
- Mỗi bé một hồ sơ riêng với danh sách ứng dụng, số phút được xem mỗi ngày và giờ đi ngủ riêng.
- Khi hết giờ hoặc đến giờ ngủ, TV hiện một lời nhắn nhẹ nhàng thay vì tắt đột ngột.

**Cho ba mẹ**

- Mọi ứng dụng ngoài danh sách, kể cả Cài đặt và cửa hàng ứng dụng, đều cần mã PIN.
- Điều khiển từ điện thoại: tạm dừng TV, cho thêm giờ, tắt ứng dụng đang mở, mở nhanh một
  ứng dụng, phát một video YouTube, hẹn giờ tắt và bật lại TV.
- Xem hôm nay bé đã xem gì, bao lâu, và biểu đồ 7 ngày gần nhất.
- Có tiếng Việt và tiếng Anh, trên cả TV lẫn điện thoại.

## Cần chuẩn bị

- Một Android TV box chạy Android 9 trở lên.
- Điện thoại và box dùng chung mạng Wi‑Fi nhà.
- Một người rành máy tính cài giúp ứng dụng lên box. Việc này chỉ làm một lần, mất khoảng
  15 phút.

## Cài đặt

**1. Nhờ cài ứng dụng lên box**

Gửi cho người cài giúp đường dẫn tới trang
[Hướng dẫn phát triển](DEVELOPMENT.md#first-install-on-a-box). Ở đó có đủ các bước và file cài
đặt (trang viết bằng tiếng Anh). Khi họ làm xong, TV hiện màn hình "Tạo mã PIN cho ba mẹ".

**2. Tạo mã PIN**

Dùng remote nhập 4 đến 8 chữ số, bấm OK, rồi nhập lại lần nữa. Đừng để các bé thấy mã này.

**3. Mở trang quản lý trên điện thoại**

Trên TV, chọn nút "Ba mẹ" và nhập mã PIN: góc trên màn hình hiện một mã QR. Dùng camera
điện thoại quét mã đó, rồi nhập mã PIN trên điện thoại. Trên iPhone, bấm Chia sẻ → "Thêm
vào MH chính" để có biểu tượng như một ứng dụng.

**4. Thiết lập cho từng bé**

Vào tab **Quản lý**, chọn hồ sơ, rồi đặt tên, số phút mỗi ngày, giờ đi ngủ và bật các ứng
dụng bé được dùng. Bấm "Lưu thay đổi".

Muốn đổi ngôn ngữ, kéo xuống cuối tab **Quản lý** và chọn "Tiếng Việt" hoặc "English".
Lựa chọn này áp dụng cho cả màn hình TV và trang quản lý.

### Nên làm thêm

- **Chặn trang người lớn** trong mọi ứng dụng. Nhờ người cài giúp bật bộ lọc gia đình khi
  cài ứng dụng.
- **Gỡ trình duyệt, trình tải file và các kho ứng dụng lạ** khỏi box, vì đó là đường dẫn
  đến nội dung không phù hợp.
- **Dùng hồ sơ trẻ em trong YouTube** nếu bé còn nhỏ. Ứng dụng này quản lý việc bé được mở
  ứng dụng nào và trong bao lâu, chứ không lọc nội dung bên trong từng ứng dụng.

## Dùng hằng ngày

### Trên TV

- **Đổi hồ sơ:** chọn hình tròn ở góc trên bên phải, rồi chọn bé đang xem.
- **Chế độ ba mẹ:** chọn nút "Ba mẹ" và nhập mã PIN. Mọi ứng dụng mở khóa trong 15 phút và
  không tính vào giờ xem của bé. Chọn "Khóa lại" khi xong.
- **Khi TV bị khóa:** chọn "Ba mẹ mở khóa", nhập mã PIN, rồi chọn cho thêm 15, 30 hoặc
  60 phút.

### Trên điện thoại

| Tab | Dùng để |
|---|---|
| **Điều khiển** | Tạm dừng TV, cho thêm giờ, tắt hoặc mở ứng dụng, phát link YouTube, đổi bé đang xem, hẹn giờ tắt, bật lại TV |
| **Quản lý** | Sửa hồ sơ của từng bé, chọn ứng dụng được phép, đổi mã PIN, đổi ngôn ngữ |
| **Báo cáo** | Xem hôm nay bé xem gì và thời gian xem trong 7 ngày |

Một vài mẹo:

- **Phát video YouTube:** trong ứng dụng YouTube trên điện thoại, bấm Chia sẻ → Sao chép
  đường liên kết, rồi dán vào ô "Dán link YouTube" và bấm "Phát".
- **Hẹn giờ tắt:** hết giờ, TV đóng ứng dụng đang xem và tắt màn hình. Bật lại bằng remote
  hoặc nút "Bật TV" trên điện thoại.
- **Khóa việc đổi hồ sơ:** bật "Đổi hồ sơ trên TV cần mã PIN" trong tab Quản lý để bé nhỏ
  không tự chuyển sang hồ sơ của anh chị.

## Câu hỏi thường gặp

**Quên mã PIN thì làm sao?**
Ứng dụng phải được gỡ rồi cài lại; hãy nhờ người đã cài giúp. Mã PIN và toàn bộ hồ sơ sẽ
bị xóa, bạn thiết lập lại từ đầu.

**Điện thoại không mở được trang quản lý.**
Kiểm tra điện thoại đang dùng đúng Wi‑Fi nhà, không phải 4G. Nếu vẫn không được, địa chỉ
IP của box có thể đã đổi; xem lại trong Cài đặt → Mạng trên TV. Bạn nên đặt IP cố định
cho box trong bộ phát Wi‑Fi.

**Ra ngoài có điều khiển được không?**
Không. Trang quản lý chỉ hoạt động khi điện thoại ở cùng mạng Wi‑Fi với box.

**Bấm "Bật TV" mà màn hình TV không sáng.**
Nút này đánh thức box. Chiếc TV có tự bật theo hay không tùy vào tính năng HDMI‑CEC của
TV; nếu không, hãy bật TV bằng remote của TV.

**Bé có vượt qua được không?**
Với trẻ nhỏ thì đây là rào chắn chắc chắn. Một đứa trẻ lớn rành công nghệ vẫn có thể khôi
phục cài đặt gốc của box bằng nút cứng, và việc đó xóa toàn bộ thiết lập.

**Cập nhật ứng dụng có mất dữ liệu không?**
Không. Bản mới giữ nguyên mã PIN, hồ sơ và thời gian đã xem.

**Muốn gỡ ứng dụng thì sao?**
Nhờ người đã cài giúp làm theo mục "Uninstall" trong
[Hướng dẫn phát triển](DEVELOPMENT.md#uninstall). Box trở lại màn hình chính gốc của Google.

## Lưu ý về an toàn

- Trang quản lý không mã hóa kết nối, nên chỉ dùng trong mạng nhà. Đừng mở cổng 8765 ra
  Internet.

## Dành cho người phát triển

Hướng dẫn dựng từ mã nguồn, kiến trúc và API nằm trong [DEVELOPMENT.md](DEVELOPMENT.md)
(tiếng Anh).
