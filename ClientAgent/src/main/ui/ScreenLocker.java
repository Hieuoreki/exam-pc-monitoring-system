package main.ui;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Platform;
import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class ScreenLocker {

    private static List<Stage> lockStages = new ArrayList<>();
    private static Thread watcherThread;
    private static volatile boolean isRunning = false;

    public static void show() {
        isRunning = false;
        if (watcherThread != null && watcherThread.isAlive()) {
            watcherThread.interrupt();
        }

        if (!lockStages.isEmpty()) {
            for (Stage stage : lockStages) {
                stage.close();
            }
            lockStages.clear();
        }

        System.out.println("[SCREEN LOCKER] Đang Khóa tất cả màn hình...");

        List<Screen> screens = Screen.getScreens();

        for (Screen screen : screens) {
            Stage stage = new Stage();
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setAlwaysOnTop(true);

            Pane pane = new Pane();
            pane.setStyle("-fx-background-color: white;");
            Scene scene = new Scene(pane);
            stage.setScene(scene);

            Rectangle2D bounds = screen.getBounds();
            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());

            stage.show();
            lockStages.add(stage);
        }

        isRunning = true;
        watcherThread = new Thread(() -> {
            while (isRunning) {
                try {
                    Platform.runLater(() -> {
                        for (Stage stage : lockStages) {
                            if (stage != null && !stage.isFocused()) {
                                stage.toFront();
                                stage.requestFocus();
                            }
                        }
                    });
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
            System.out.println("[SCREEN LOCKER] Đã dừng luồng canh gác.");
        });

        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    public static void hide() {
        System.out.println("[SCREEN LOCKER] Đang ẩn màn hình khóa...");
        isRunning = false;
        if (watcherThread != null) {
            watcherThread.interrupt();
            watcherThread = null;
        }

        Platform.runLater(() -> {
            for (Stage stage : lockStages) {
                stage.close();
            }
            lockStages.clear();
        });
    }
}