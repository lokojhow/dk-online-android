package com.dktelecom.dkonline;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@CapacitorPlugin(name = "RouterBridge")
public class RouterBridgePlugin extends Plugin {

    private String findGateway() {
        ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return null;
        Network active = cm.getActiveNetwork();
        if (active == null) return null;
        LinkProperties properties = cm.getLinkProperties(active);
        if (properties == null) return null;
        for (RouteInfo route : properties.getRoutes()) {
            if (route.isDefaultRoute() && route.hasGateway()) {
                InetAddress gateway = route.getGateway();
                if (gateway != null) return gateway.getHostAddress();
            }
        }
        return null;
    }

    @PluginMethod
    public void getGateway(PluginCall call) {
        JSObject result = new JSObject();
        String gateway = findGateway();
        result.put("gateway", gateway == null ? "" : gateway);

        ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        String transport = "unknown";
        if (cm != null && cm.getActiveNetwork() != null) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transport = "wifi";
                else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transport = "cellular";
                else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transport = "ethernet";
            }
        }
        result.put("transport", transport);
        call.resolve(result);
    }

    @PluginMethod
    public void detectRouter(PluginCall call) {
        String host = call.getString("host");
        if (host == null || host.trim().isEmpty()) host = findGateway();
        if (host == null || host.trim().isEmpty()) host = "192.168.0.1";
        host = host.trim().replace("http://", "").replace("https://", "");
        final String routerHost = host;

        getBridge().execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("http://" + routerHost + "/");
                long start = System.currentTimeMillis();
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3500);
                connection.setReadTimeout(3500);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "DKOnline-Android/1.0");
                int status = connection.getResponseCode();
                InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                StringBuilder body = new StringBuilder();
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null && body.length() < 32768) body.append(line).append('\n');
                    }
                }
                String lower = body.toString().toLowerCase(Locale.ROOT);
                String model = "Roteador detectado";
                if (lower.contains("mercusys") && lower.contains("ac12g")) model = "Mercusys AC12G";
                else if (lower.contains("mercusys")) model = "Mercusys";
                else if (lower.contains("tp-link")) model = "TP-Link";

                JSObject result = new JSObject();
                result.put("reachable", status >= 200 && status < 500);
                result.put("status", status);
                result.put("url", "http://" + routerHost);
                result.put("model", model);
                result.put("latencyMs", System.currentTimeMillis() - start);
                call.resolve(result);
            } catch (Exception error) {
                JSObject result = new JSObject();
                result.put("reachable", false);
                result.put("status", 0);
                result.put("url", "http://" + routerHost);
                result.put("model", "");
                result.put("error", error.getClass().getSimpleName());
                call.resolve(result);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
