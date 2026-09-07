package main.util;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.DecimalFormat;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

public class SystemMonitor {

    public static Map<String, Object> getSystemConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("OS Name", System.getProperty("os.name"));
        config.put("OS Version", System.getProperty("os.version"));
        config.put("OS Arch", System.getProperty("os.arch"));
        config.put("CPU Cores", Runtime.getRuntime().availableProcessors());

        long maxMemory = Runtime.getRuntime().maxMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();

        config.put("JVM Max RAM", bytesToMegabytes(maxMemory));
        config.put("JVM Total RAM", bytesToMegabytes(totalMemory));
        config.put("JVM Free RAM", bytesToMegabytes(freeMemory));
        return config;
    }

    private static String bytesToMegabytes(long bytes) {
        DecimalFormat df = new DecimalFormat("#.##");
        return df.format(bytes / (1024.0 * 1024.0)) + " MB";
    }

    public static List<String> getRunningProcesses() {
        return ProcessHandle.allProcesses()
                .map(ph -> ph.info().command().orElse("N/A"))
                .map(path -> new File(path).getName())
                .filter(name -> !name.isEmpty() && !name.equals("N/A"))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Chụp màn hình máy trạm và mã hóa thành Base64 (JPG)
     */
    public static String captureScreenBase64() {
        try {
            Robot robot = new Robot();
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Rectangle screenRectangle = new Rectangle(screenSize);
            BufferedImage image = robot.createScreenCapture(screenRectangle);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Tắt tiến trình từ xa theo tên
     */
    public static boolean killProcess(String processName) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            Process process;
            if (os.contains("win")) {
                process = Runtime.getRuntime().exec("taskkill /F /IM " + processName);
            } else {
                process = Runtime.getRuntime().exec("pkill -f " + processName);
            }
            return process.waitFor() == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}