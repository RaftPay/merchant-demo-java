import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * RaftPay 回调通知接收端
 *
 * 启动方式:
 *   java Callback
 *
 * 回调数据格式:
 *   POST body: {"data": "Base64编码的RSA加密数据"}
 *   使用平台公钥解密后得到业务数据 JSON
 *
 * 解密后字段:
 *   - orderId          string 平台订单号
 *   - merchantOrderNo  string 商户订单号
 *   - orderType        string PayIn(代收) 或 PayOut(代付)
 *   - amount           string 订单金额
 *   - fee              string 手续费
 *   - status           number 2=成功, 3=失败
 *   - payType          string 支付类型
 *   - currency         string 货币代码
 *   - processCurrency  string 实际支付货币
 *   - processAmount    string 实际支付金额
 *   - merchantCustomize string 自定义字段（原样返回）
 *   - createTime       number 毫秒时间戳
 *
 * 响应要求:
 *   HTTP 200，返回 "success" 字符串。否则平台会重试（最多10次，约48小时）。
 */
public class Callback {

    public static void main(String[] args) throws Exception {
        // ============================================================
        // 1. 加载配置
        // ============================================================
        Properties config = new Properties();
        try {
            config.load(new FileInputStream("config.properties"));
        } catch (Exception e) {
            System.err.println("无法加载 config.properties，请先从 config.example.properties 复制并填入真实凭证");
            System.exit(1);
        }

        String platformPublicKey = config.getProperty("platformPublicKey");
        int port = Integer.parseInt(config.getProperty("callbackPort", "8080"));

        // ============================================================
        // 2. 创建 HTTP 服务
        // ============================================================
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> handleCallback(exchange, platformPublicKey));
        server.setExecutor(null);
        server.start();

        System.out.println("[RaftPay Callback] 回调服务已启动，监听端口: " + port);
        System.out.println("[RaftPay Callback] 回调地址: http://localhost:" + port + "/");
    }

    private static void handleCallback(HttpExchange exchange, String platformPublicKey) throws IOException {
        // 非 POST 请求直接返回 success
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, "success");
            return;
        }

        // ============================================================
        // 3. 读取请求体
        // ============================================================
        BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        String rawBody = sb.toString();

        Map<String, Object> body;
        try {
            body = JsonUtil.parseJson(rawBody);
        } catch (Exception e) {
            System.err.println("[RaftPay Callback] JSON 解析失败: " + rawBody);
            sendResponse(exchange, "success");
            return;
        }

        if (body.get("data") == null) {
            System.err.println("[RaftPay Callback] 缺少 data 字段");
            sendResponse(exchange, "success");
            return;
        }

        // ============================================================
        // 4. 使用平台公钥解密
        // ============================================================
        Map<String, Object> notice;
        try {
            String decrypted = RaftPayCrypto.decryptWithPublicKey(body.get("data").toString(), platformPublicKey);
            notice = JsonUtil.parseJson(decrypted);
        } catch (Exception e) {
            System.err.println("[RaftPay Callback] 解密失败: " + e.getMessage());
            sendResponse(exchange, "success");
            return;
        }

        // ============================================================
        // 5. 处理业务逻辑
        // ============================================================
        String orderId = strVal(notice.get("orderId"));
        String merchantOrderNo = strVal(notice.get("merchantOrderNo"));
        String orderType = strVal(notice.get("orderType"));  // PayIn 或 PayOut
        int status = intVal(notice.get("status"));            // 2=成功, 3=失败
        String amount = strVal(notice.get("amount"));
        String fee = strVal(notice.get("fee"));

        System.err.printf("[RaftPay Callback] orderId=%s merchantOrderNo=%s type=%s status=%d amount=%s fee=%s%n",
                orderId, merchantOrderNo, orderType, status, amount, fee);

        // 注意: 必须做幂等性判断，同一订单可能收到多次回调
        // 建议根据 merchantOrderNo 查询本地订单状态，已处理则直接返回 success

        if (status == 2) {
            // ===== 支付成功 =====
            // TODO: 更新本地订单状态为成功
            // TODO: 代收 — 给用户加款 / 代付 — 确认打款完成
            System.err.println("[RaftPay Callback] 订单 " + merchantOrderNo + " 支付成功");

        } else if (status == 3) {
            // ===== 支付失败 =====
            // TODO: 更新本地订单状态为失败
            // TODO: 代付失败 — 解冻用户余额
            System.err.println("[RaftPay Callback] 订单 " + merchantOrderNo + " 支付失败");

        } else {
            System.err.println("[RaftPay Callback] 订单 " + merchantOrderNo + " 未知状态: " + status);
        }

        // ============================================================
        // 6. 必须返回 "success"，否则平台会重试
        // ============================================================
        sendResponse(exchange, "success");
    }

    private static void sendResponse(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes("UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private static String strVal(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    private static int intVal(Object obj) {
        if (obj == null) return -1;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception e) { return -1; }
    }
}
