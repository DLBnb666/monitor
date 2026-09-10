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

    public static void main(String[] args) throws Exception {
        Class<?> atCls = Class.forName("android.app.ActivityThread");
        Object at = atCls.getMethod("systemMain").invoke(null);
        Context ctx = (Context) atCls.getMethod("getSystemContext").invoke(at);

        Looper.prepare();
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_SECURE,
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
        tv.setText("init...");
        root.addView(tv);

        root.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy;
            int sx, sy;
            boolean moved;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX(); dy = e.getRawY();
                        sx = lp.x; sy = lp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float mx = e.getRawX() - dx, my = e.getRawY() - dy;
                        if (Math.abs(mx) > 10 || Math.abs(my) > 10) moved = true;
                        if (moved) {
                            lp.x = sx + (int) mx;
                            lp.y = sy + (int) my;
                            wm.updateViewLayout(layout, lp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            try { wm.removeView(layout); } catch (Exception ex) {}
                            System.exit(0);
                        }
                        return true;
                }
                return false;
            }
        });

        layout = root;
        wm.addView(layout, lp);

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        final String text = buildText();
                        tv.post(new Runnable() {
                            @Override
                            public void run() { tv.setText(text); }
                        });
                        Thread.sleep(1000);
                    } catch (Exception ex) {
                        try { Thread.sleep(1000); } catch (Exception e2) {}
                    }
                }
            }
        }).start();

        Looper.loop();
    }

    static java.util.LinkedHashMap<String, long[]> lastCpu = null;

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
        long dt = now[0] - last[0];
        long di = now[1] - last[1];
        if (dt <= 0) return 0;
        int p = (int) ((dt - di) * 100 / dt);
        return p < 0 ? 0 : (p > 100 ? 100 : p);
    }

    static long[] readMem() throws Exception {
        BufferedReader br = new Buf
