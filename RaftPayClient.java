import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

/**
 * RaftPay 商户 API 客户端
 *
 * 支持接口:
 *   1. 代收订单创建  POST /order/api/merchant/v1/order/pay
 *   2. 代付订单创建  POST /order/api/merchant/v1/order/payout
 *   3. 订单状态查询  GET  /order/api/merchant/v1/order/status
 *   4. 余额查询      GET  /order/api/merchant/v1/account/balance
 *
 * Java 8+ 兼容，零第三方依赖
 */
public class RaftPayClient {

    private final String merchantId;
    private final String merchantPrivateKey;
    private final String apiBaseUrl;
    private final int timeout;

    /**
     * @param merchantId         商户 ID
     * @param merchantPrivateKey 商户私钥（Base64）
     * @param apiBaseUrl         API 地址（不带末尾斜杠）
     */
    public RaftPayClient(String merchantId, String merchantPrivateKey, String apiBaseUrl) {
        this(merchantId, merchantPrivateKey, apiBaseUrl, 30);
    }

    public RaftPayClient(String merchantId, String merchantPrivateKey, String apiBaseUrl, int timeoutSeconds) {
        this.merchantId = merchantId;
        this.merchantPrivateKey = merchantPrivateKey;
        this.apiBaseUrl = apiBaseUrl.replaceAll("/+$", "");
        this.timeout = timeoutSeconds * 1000;
    }

    // ========================================================================
    // 1. 代收订单创建
    // ========================================================================

    /**
     * 创建代收订单
     *
     * @param params 业务参数:
     *   - merchantOrderNo string 必填 商户订单号（最大60字符）
     *   - amount          string 必填 金额，如 "100" 或 "100.00"
     *   - notifyUrl       string 必填 回调地址（最大150字符）
     *   - currency        string 选填 固定 PKR
     *   - payType         string 选填 EASYPAISA 或 JAZZCASH
     *   - description     string 选填 订单描述
     *   - payerMobile     string 选填 格式 03xxxxxxxxx
     *   - returnUrl       string 选填 支付完成后跳转地址
     * @return API 响应
     */
    public Map<String, Object> createDeposit(Map<String, Object> params) throws Exception {
        String url = apiBaseUrl + "/order/api/merchant/v1/order/pay";
        return postEncrypted(url, params);
    }

    // ========================================================================
    // 2. 代付订单创建
    // ========================================================================

    /**
     * 创建代付订单
     *
     * @param params 业务参数:
     *   - merchantOrderNo string 必填 商户订单号
     *   - amount          string 必填 金额
     *   - notifyUrl       string 必填 回调地址
     *   - payoutMethod    string 必填 MWALLET 或 IBFT
     *   - payerMobile     string 必填 格式 03xxxxxxxxx
     *   - currency        string 选填 固定 PKR
     *   - payType         string 条件 MWALLET 时必填: EASYPAISA 或 JAZZCASH
     *   - accountNumber   string 条件 IBFT 时必填
     *   - accountName     string 条件 IBFT 时必填
     *   - bankCode        string 条件 IBFT 时必填
     *   - description     string 选填
     * @return API 响应
     */
    public Map<String, Object> createPayout(Map<String, Object> params) throws Exception {
        String url = apiBaseUrl + "/order/api/merchant/v1/order/payout";
        return postEncrypted(url, params);
    }

    // ========================================================================
    // 3. 订单状态查询（代收/代付共用）
    // ========================================================================

    /**
     * 查询订单状态
     *
     * @param merchantOrderNo 商户订单号
     * @return API 响应，data.status: 0=待支付 1=处理中 2=成功 3=失败
     */
    public Map<String, Object> queryOrderStatus(String merchantOrderNo) throws Exception {
        String url = apiBaseUrl + "/order/api/merchant/v1/order/status";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("merchantOrderNo", merchantOrderNo);
        data.put("timestamp", System.currentTimeMillis() / 1000);
        return getEncrypted(url, data);
    }

    // ========================================================================
    // 4. 余额查询
    // ========================================================================

    /**
     * 查询商户余额
     *
     * @return API 响应，data 包含 availableMoney, frozenMoney, unsettledMoney
     */
    public Map<String, Object> queryBalance() throws Exception {
        String url = apiBaseUrl + "/order/api/merchant/v1/account/balance";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", System.currentTimeMillis() / 1000);
        return getEncrypted(url, data);
    }

    // ========================================================================
    // 内部方法
    // ========================================================================

    /**
     * POST 请求（加密业务数据）
     */
    private Map<String, Object> postEncrypted(String url, Map<String, Object> bizData) throws Exception {
        String encryptedData = RaftPayCrypto.encryptWithPrivateKey(
                JsonUtil.toJson(bizData),
                merchantPrivateKey
        );

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("merchantId", merchantId);
        requestBody.put("data", encryptedData);
        requestBody.put("timestamp", System.currentTimeMillis() / 1000);

        return httpPost(url, requestBody);
    }

    /**
     * GET 请求（加密业务数据作为 query 参数）
     */
    private Map<String, Object> getEncrypted(String url, Map<String, Object> bizData) throws Exception {
        String encryptedData = RaftPayCrypto.encryptWithPrivateKey(
                JsonUtil.toJson(bizData),
                merchantPrivateKey
        );

        String queryString = "merchantId=" + URLEncoder.encode(merchantId, "UTF-8")
                + "&data=" + URLEncoder.encode(encryptedData, "UTF-8");

        return httpGet(url + "?" + queryString);
    }

    /**
     * 发送 POST 请求
     */
    private Map<String, Object> httpPost(String urlStr, Map<String, Object> body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("User-Agent", "RaftPay-SDK-Java/1.0");
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        conn.setDoOutput(true);

        byte[] payload = JsonUtil.toJson(body).getBytes("UTF-8");
        conn.getOutputStream().write(payload);
        conn.getOutputStream().flush();

        return readResponse(conn);
    }

    /**
     * 发送 GET 请求
     */
    private Map<String, Object> httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "RaftPay-SDK-Java/1.0");
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);

        return readResponse(conn);
    }

    /**
     * 读取 HTTP 响应
     */
    private Map<String, Object> readResponse(HttpURLConnection conn) throws Exception {
        int httpCode = conn.getResponseCode();
        InputStream is = (httpCode >= 200 && httpCode < 300) ? conn.getInputStream() : conn.getErrorStream();

        if (is == null) {
            throw new RuntimeException("HTTP 请求失败, 状态码: " + httpCode + ", 无响应体");
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();

        String responseBody = sb.toString();
        try {
            return JsonUtil.parseJson(responseBody);
        } catch (Exception e) {
            throw new RuntimeException("JSON 解析失败, HTTP " + httpCode + ", 响应: " + responseBody, e);
        }
    }
}
