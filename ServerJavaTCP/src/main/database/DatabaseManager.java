package main.database;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {
    private static DatabaseManager instance;
    private static final String DB_URL = "jdbc:sqlite:exam_monitor.db";

    private DatabaseManager() {
        initTables();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private void initTables() {
        // 1. BẢNG VI PHẠM: Ràng buộc UNIQUE(student_code, process_name)
        String sqlLogs = "CREATE TABLE IF NOT EXISTS violation_logs ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "machine_name TEXT NOT NULL, "
                + "student_name TEXT, "
                + "student_code TEXT NOT NULL, "
                + "process_name TEXT NOT NULL, "
                + "violation_time TEXT NOT NULL, "
                + "UNIQUE(student_code, process_name)"
                + ");";

        // 2. BẢNG CHỤP MÀN HÌNH: Cột proctor_name lưu tên giám thị
        String sqlScreenshots = "CREATE TABLE IF NOT EXISTS screenshots ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "machine_name TEXT NOT NULL, "
                + "student_name TEXT, "
                + "student_code TEXT, "
                + "proctor_name TEXT NOT NULL, "
                + "file_path TEXT NOT NULL, "
                + "captured_time TEXT NOT NULL"
                + ");";

        // 3. BẢNG QUẢN LÝ GIÁM THỊ
        String sqlProctors = "CREATE TABLE IF NOT EXISTS proctors ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT UNIQUE NOT NULL, "
                + "password TEXT NOT NULL, "
                + "full_name TEXT NOT NULL, "
                + "status TEXT NOT NULL, "
                + "created_at TEXT NOT NULL"
                + ");";

        // 4. BẢNG THEO DÕI THỜI GIAN VÀO / RA PHÒNG THI
        String sqlAttendance = "CREATE TABLE IF NOT EXISTS exam_attendance ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "student_code TEXT NOT NULL, "
                + "student_name TEXT, "
                + "machine_name TEXT NOT NULL, "
                + "join_time TEXT NOT NULL, "
                + "leave_time TEXT"
                + ");";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(sqlLogs);
            stmt.execute(sqlScreenshots);
            stmt.execute(sqlProctors);
            stmt.execute(sqlAttendance);

            // Tự động bổ sung cột proctor_name nếu file db cũ chưa có
            try {
                stmt.execute("ALTER TABLE screenshots ADD COLUMN proctor_name TEXT DEFAULT 'Hệ thống';");
            } catch (SQLException ignored) {
                // Đã có cột thì bỏ qua
            }

            // Tạo sẵn 1 tài khoản Admin mặc định
            String checkAdmin = "SELECT COUNT(*) FROM proctors WHERE username = 'admin'";
            ResultSet rs = stmt.executeQuery(checkAdmin);
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute("INSERT INTO proctors (username, password, full_name, status, created_at) "
                        + "VALUES ('admin', 'admin123', 'Giám Thị Trưởng', 'APPROVED', datetime('now', 'localtime'))");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // --- LOGIC 1: GHI NHẬN VI PHẠM (CHỐNG TRÙNG LẶP CHO TỪNG TIẾN TRÌNH) ---
    public synchronized boolean logViolation(String machineName, String studentName, String studentCode, String processName, String violationTime) {
        String sql = "INSERT OR IGNORE INTO violation_logs (machine_name, student_name, student_code, process_name, violation_time) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, machineName);
            pstmt.setString(2, studentName);
            pstmt.setString(3, studentCode != null ? studentCode : "UNKNOWN");
            pstmt.setString(4, processName);
            pstmt.setString(5, violationTime);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- LOGIC 2: LƯU BẰNG CHỨNG ẢNH KÈM TÊN GIÁM THỊ ---
    public synchronized boolean logScreenshot(String machineName, String studentName, String studentCode, String proctorName, String filePath, String capturedTime) {
        String sql = "INSERT INTO screenshots (machine_name, student_name, student_code, proctor_name, file_path, captured_time) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, machineName);
            pstmt.setString(2, studentName);
            pstmt.setString(3, studentCode);
            pstmt.setString(4, (proctorName != null && !proctorName.isEmpty()) ? proctorName : "Hệ thống");
            pstmt.setString(5, filePath);
            pstmt.setString(6, capturedTime);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- LOGIC 3: GHI NHẬN THỜI GIAN VÀO / RA PHÒNG THI ---
    public synchronized int logStudentJoin(String studentCode, String studentName, String machineName, String joinTime) {
        String sql = "INSERT INTO exam_attendance (student_code, student_name, machine_name, join_time) VALUES (?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, studentCode);
            pstmt.setString(2, studentName);
            pstmt.setString(3, machineName);
            pstmt.setString(4, joinTime);
            pstmt.executeUpdate();
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1); // Trả về ID của phiên thi này
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public synchronized void logStudentLeave(int sessionId, String leaveTime) {
        if (sessionId <= 0) return;
        String sql = "UPDATE exam_attendance SET leave_time = ? WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, leaveTime);
            pstmt.setInt(2, sessionId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // --- LOGIC 4: CÁC THAO TÁC QUẢN LÝ GIÁM THỊ ---
    public synchronized boolean registerProctor(String username, String password, String fullName) {
        String sql = "INSERT INTO proctors (username, password, full_name, status, created_at) VALUES (?, ?, ?, 'PENDING', datetime('now', 'localtime'))";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, fullName);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized boolean addProctorDirectly(String username, String password, String fullName) {
        String sql = "INSERT INTO proctors (username, password, full_name, status, created_at) VALUES (?, ?, ?, 'APPROVED', datetime('now', 'localtime'))";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, fullName);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized String checkLogin(String username, String password) {
        String sql = "SELECT password, status FROM proctors WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                if (rs.getString("password").equals(password)) {
                    return "APPROVED".equalsIgnoreCase(rs.getString("status")) ? "SUCCESS" : "PENDING";
                }
                return "WRONG_PASS";
            }
            return "NOT_FOUND";
        } catch (SQLException e) {
            return "ERROR";
        }
    }

    public synchronized String getProctorFullName(String username) {
        String sql = "SELECT full_name FROM proctors WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("full_name");
        } catch (SQLException ignored) {}
        return username;
    }

    public synchronized List<Map<String, String>> getProctorsByStatus(String status) {
        List<Map<String, String>> list = new ArrayList<>();
        String sql = "SELECT id, username, full_name, created_at FROM proctors WHERE status = ? ORDER BY id DESC";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, String> item = new HashMap<>();
                item.put("id", String.valueOf(rs.getInt("id")));
                item.put("username", rs.getString("username"));
                item.put("fullName", rs.getString("full_name"));
                item.put("createdAt", rs.getString("created_at"));
                list.add(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public synchronized boolean approveProctor(int id) {
        String sql = "UPDATE proctors SET status = 'APPROVED' WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized boolean deleteProctor(int id) {
        String sql = "DELETE FROM proctors WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }
}