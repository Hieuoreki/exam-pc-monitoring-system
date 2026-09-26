package main.ui;

import java.net.InetAddress;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import main.database.DatabaseManager;

public class ServerController implements Initializable {

    private static ServerController instance;

    @FXML private Label lblIp;
    @FXML private Label lblPort;

    @FXML private TableView<Map<String, String>> tableApproved;
    @FXML private TableColumn<Map<String, String>, String> colApprovedUser;
    @FXML private TableColumn<Map<String, String>, String> colApprovedName;
    @FXML private TableColumn<Map<String, String>, String> colApprovedTime;

    @FXML private TableView<Map<String, String>> tablePending;
    @FXML private TableColumn<Map<String, String>, String> colPendingUser;
    @FXML private TableColumn<Map<String, String>, String> colPendingName;
    @FXML private TableColumn<Map<String, String>, String> colPendingTime;

    private ObservableList<Map<String, String>> listApproved = FXCollections.observableArrayList();
    private ObservableList<Map<String, String>> listPending = FXCollections.observableArrayList();

    public static ServerController getInstance() {
        return instance;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        instance = this;

        // 1. Hiển thị thông số mạng
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            lblIp.setText("IP Máy Chủ: " + ip);
        } catch (Exception e) {
            lblIp.setText("IP Máy Chủ: 127.0.0.1");
        }

        // 2. Gán ánh xạ cột cho bảng Approved
        colApprovedUser.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get("username")));
        colApprovedName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get("fullName")));
        colApprovedTime.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get("createdAt")));
        tableApproved.setItems(listApproved);
        tableApproved.setPlaceholder(new Label("Chưa có giám thị nào."));

        // 3. Gán ánh xạ cột cho bảng Pending
        colPendingUser.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get("username")));
        colPendingName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get("fullName")));
        colPendingTime.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get("createdAt")));
        tablePending.setItems(listPending);
        tablePending.setPlaceholder(new Label("Không có yêu cầu chờ duyệt."));

        // 4. Nạp dữ liệu ban đầu
        refreshProctorTables();
    }

    public void refreshProctorTables() {
        Platform.runLater(() -> {
            listApproved.setAll(DatabaseManager.getInstance().getProctorsByStatus("APPROVED"));
            listPending.setAll(DatabaseManager.getInstance().getProctorsByStatus("PENDING"));
        });
    }

    @FXML
    private void handleAddProctor() {
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Thêm Giám Thị");
        dialog.setHeaderText("Tài khoản tạo trực tiếp sẽ được kích hoạt (APPROVED) ngay.");

        ButtonType btnSave = new ButtonType("Lưu", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSave, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField tfUser = new TextField();
        PasswordField tfPass = new PasswordField();
        TextField tfName = new TextField();

        grid.add(new Label("Username:"), 0, 0); grid.add(tfUser, 1, 0);
        grid.add(new Label("Mật khẩu:"), 0, 1);  grid.add(tfPass, 1, 1);
        grid.add(new Label("Họ tên:"), 0, 2);   grid.add(tfName, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(b -> (b == btnSave) ? Map.of("u", tfUser.getText().trim(), "p", tfPass.getText().trim(), "n", tfName.getText().trim()) : null);

        Optional<Map<String, String>> res = dialog.showAndWait();
        res.ifPresent(d -> {
            if (d.get("u").isEmpty() || d.get("p").isEmpty() || d.get("n").isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Vui lòng nhập đủ thông tin!");
                return;
            }
            if (DatabaseManager.getInstance().addProctorDirectly(d.get("u"), d.get("p"), d.get("n"))) {
                refreshProctorTables();
            } else {
                showAlert(Alert.AlertType.ERROR, "Tên đăng nhập đã tồn tại!");
            }
        });
    }

    @FXML
    private void handleApprove() {
        Map<String, String> item = tablePending.getSelectionModel().getSelectedItem();
        if (item == null) {
            showAlert(Alert.AlertType.WARNING, "Hãy chọn tài khoản cần duyệt!");
            return;
        }
        int id = Integer.parseInt(item.get("id"));
        if (DatabaseManager.getInstance().approveProctor(id)) {
            refreshProctorTables();
        }
    }

    @FXML
    private void handleDeleteApproved() {
        deleteProctorFromTable(tableApproved);
    }

    @FXML
    private void handleDeletePending() {
        deleteProctorFromTable(tablePending);
    }

    private void deleteProctorFromTable(TableView<Map<String, String>> table) {
        Map<String, String> item = table.getSelectionModel().getSelectedItem();
        if (item == null) {
            showAlert(Alert.AlertType.WARNING, "Hãy chọn dòng cần xóa!");
            return;
        }
        int id = Integer.parseInt(item.get("id"));
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Bạn có chắc muốn xóa: " + item.get("username") + "?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(res -> {
            if (res == ButtonType.YES) {
                DatabaseManager.getInstance().deleteProctor(id);
                refreshProctorTables();
            }
        });
    }

    private void showAlert(Alert.AlertType type, String msg) {
        Alert a = new Alert(type, msg, ButtonType.OK);
        a.show();
    }
}