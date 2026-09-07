package main.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:exam_monitor.db";
    private static DatabaseManager instance;

    private DatabaseManager() {
        initDatabase();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private void initDatabase() {
        // 1. Bảng lưu vi phạm tiến trình
        String sqlCreateViolationTable = "CREATE TABLE IF NOT EXISTS violation_logs ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "machine_name TEXT NOT NULL, "
                + "student_name TEXT, "
                + "student_code TEXT, "
                + "process_name TEXT NOT NULL, "
                + "violation_time TEXT NOT NULL"
                + ");";

        // 2. Bảng lưu lịch sử chụp màn hình và đường dẫn file ảnh
        String sqlCreateScreenshotTable = "CREATE TABLE IF NOT EXISTS screenshots ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "machine_name TEXT NOT NULL, "
                + "student_name TEXT, "
                + "student_code TEXT, "
                + "file_path TEXT NOT NULL, "
                + "captured_time TEXT NOT NULL"
                + ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sqlCreateViolationTable);
            stmt.execute(sqlCreateScreenshotTable);
            System.out.println("[Database] Đã khởi tạo bảng violation_logs và screenshots thành công.");
        } catch (SQLException e) {
            System.err.println("[Database] Lỗi khi tạo bảng: " + e.getMessage());
        }
    }

    public void logViolation(String machineName, String studentName, String studentCode, String processName, String violationTime) {
        String sql = "INSERT INTO violation_logs(machine_name, student_name, student_code, process_name, violation_time) VALUES(?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, machineName);
            pstmt.setString(2, studentName);
            pstmt.setString(3, studentCode);
            pstmt.setString(4, processName);
            pstmt.setString(5, violationTime);
            pstmt.executeUpdate();
            System.out.println("[Database] Đã lưu vi phạm của máy " + machineName + " (" + processName + ") vào SQLite.");
        } catch (SQLException e) {
            System.err.println("[Database] Lỗi lưu vi phạm: " + e.getMessage());
        }
    }

    // Hàm mới: Lưu đường dẫn file ảnh chụp màn hình vào database
    public void logScreenshot(String machineName, String studentName, String studentCode, String filePath, String capturedTime) {
        String sql = "INSERT INTO screenshots(machine_name, student_name, student_code, file_path, captured_time) VALUES(?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, machineName);
            pstmt.setString(2, studentName);
            pstmt.setString(3, studentCode);
            pstmt.setString(4, filePath);
            pstmt.setString(5, capturedTime);
            pstmt.executeUpdate();
            System.out.println("[Database] Đã lưu thông tin ảnh chụp (" + filePath + ") vào SQLite.");
        } catch (SQLException e) {
            System.err.println("[Database] Lỗi lưu screenshot: " + e.getMessage());
        }
    }
}