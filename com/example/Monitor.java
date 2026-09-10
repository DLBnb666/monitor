package com.example;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;

public class Monitor {
    static TextView tv;
    static WindowManager wm;
    static View layout;
    static WindowManager.LayoutParams lp;
    static java.util.LinkedHashMap<String, long[]> lastCpu;

    public static void main(String[] args) throws Exception {
        Class<?> atCls = Class.forName("android.app.ActivityThread");
        Object at = atCls.getMethod("systemMain").invoke(null);
        Context ctx = (Context) atCls.getMethod("getSystemContext").invoke(at);
        Looper.prepare();
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = 0;
        lp.y = 0;
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xB3101010);
        root.setPadding(16, 12, 16, 12);
        tv = new TextView(ctx);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(10);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setText("init");
        root.addView(tv);
        root.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; int sx, sy; boolean moved;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX(); dy = e.getRawY(); sx = lp.x; sy = lp.y; moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        float mx = e.getRawX() - dx, my = e.getRawY() - dy;
                        if (Math.abs(mx) > 10 || Math.abs(my) > 10) moved = true;
                        if (moved) { lp.x = sx + (int) mx; lp.y = sy + (int) my; wm.updateViewLayout(layout, lp); }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) { try { wm.removeView(layout); } catch (Exception ex) {} System.exit(0); }
                        return true;
                }
                return false;
            }
        });
        layout = root;
        wm.addView(layout, lp);
        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        final String t = buildText();
                        tv.post(new Runnable() { public void run() { tv.setText(t); } });
                        Thread.sleep(1000);
                    } catch (Exception ex) {
                        try { Thread.sleep(1000); } catch (Exception e2) {}
                    }
                }
            }
        }).start();
        Looper.loop();
    }

    static java.util.LinkedHashMap<String, long[]> readAllCpu() throws Exception {
        java.util.LinkedHashMap<String, long[]> map = new java.util.LinkedHashMap<>();
        BufferedReader br = new BufferedReader(new FileReader("/proc/stat"));
        String line;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("cpu")) {
                String[] p = line.trim().split("\\s+");
                if (p[0].equals("cpu") || p[0].matches("cpu\\d+")) {
                    long total = 0;
                    for (int i = 1; i < p.length; i++) total += Long.parseLong(p[i]);
                    long idle = Long.parseLong(p[4]) + Long.parseLong(p[5]);
                    map.put(p[0], new long[]{total, idle});
                }
            }
        }
        br.close();
        return map;
    }

    static int calcPct(long[] now, long[] last) {
        if (last == null) return 0;
        long dt = now[0] - last[0], di = now[1] - last[1];
        if (dt <= 0) return 0;
        int p = (int) ((dt - di) * 100 / dt);
        return p < 0 ? 0 : (p > 100 ? 100 : p);
    }

    static long[] readMem() throws Exception {
        BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"));
        String line;
        long total = 0, avail = 0;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("MemTotal:")) total = Long.parseLong(line.trim().split("\\s+")[1]);
            else if (line.startsWith("MemAvailable:")) { avail = Long.parseLong(line.trim().split("\\s+")[1]); break; }
        }
        br.close();
        return new long[]{total, avail};
    }

    static String readTemp() {
        try {
            BufferedReader br = new BufferedReader(new FileReader("/sys/class/thermal/thermal_zone0/temp"));
            String s = br.readLine(); br.close();
            long v = Long.parseLong(s.trim());
            if (v > 1000) v /= 1000;
            return v + "C";
        } catch (Exception e) { return "N/A"; }
    }

    static String bar(int pct, int len) {
        int f = pct * len / 100;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(i < f ? "#" : ".");
        return sb.toString();
    }

    static String buildText() throws Exception {
        java.util.LinkedHashMap<String, long[]> now = readAllCpu();
        StringBuilder sb = new StringBuilder();
        int totPct = 0;
        if (lastCpu != null && lastCpu.containsKey("cpu")) totPct = calcPct(now.get("cpu"), lastCpu.get("cpu"));
        long[] mem = readMem();
        long usedMb = (mem[0] - mem[1]) / 1024, totalMb = mem[0] / 1024;
        int memPct = mem[0] > 0 ? (int) ((mem[0] - mem[1]) * 100 / mem[0]) : 0;
        sb.append("C ").append(bar(totPct, 6)).append(" ").append(totPct).append("%\n");
        sb.append("M ").append(bar(memPct, 6)).append(" ").append(usedMb).append("/").append(totalMb).append("M\n");
        sb.append("T ").append(readTemp()).append("\n----\n");
        int i = 0;
        while (now.containsKey("cpu" + i)) {
            int p = 0;
            if (lastCpu != null && lastCpu.containsKey("cpu" + i)) p = calcPct(now.get("cpu" + i), lastCpu.get("cpu" + i));
            sb.append("c").append(i).append(" ").append(bar(p, 5)).append(" ").append(p).append("%\n");
            i++;
        }
        lastCpu = now;
        return sb.toString();
    }
}
