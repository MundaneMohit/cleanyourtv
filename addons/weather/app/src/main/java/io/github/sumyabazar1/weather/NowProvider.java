package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The weather now for a home screen that has no network access of its own:
 * {@code content://io.github.sumyabazar1.weather.now/now?lat=52.52&lon=13.41} answers one row, {@code temperature}
 * (whole °C) and {@code icon} (the name of an outline drawable in this app), or no row before the first reading.
 * <p>
 * Nothing runs until a query comes. A query answers from the last reading at once and, when that is 30 minutes old
 * or for another place, fetches once in the background (5 minutes apart after a failure), then calls notifyChange on
 * the URI so the home screen asks again. No service, no timer, no location permission: only the place it is given.
 */
public final class NowProvider extends ContentProvider {
    static final Uri BASE = Uri.parse("content://io.github.sumyabazar1.weather.now/now");
    private static final String[] COLUMNS = {"temperature", "icon"};

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean fetching = new AtomicBoolean();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor rows = new MatrixCursor(COLUMNS);
        double lat, lon;
        try {
            lat = Double.parseDouble(uri.getQueryParameter("lat"));
            lon = Double.parseDouble(uri.getQueryParameter("lon"));
        } catch (RuntimeException e) {
            return rows;
        }
        if (Math.abs(lat) > 90 || Math.abs(lon) > 180) return rows;
        final double latitude = lat, longitude = lon;
        String where = String.format(Locale.ROOT, "%.2f,%.2f", lat, lon);
        SharedPreferences last = getContext().getSharedPreferences("now", 0);
        boolean same = where.equals(last.getString("where", ""));
        long now = System.currentTimeMillis();
        boolean stale = !same || now - last.getLong("fetched", 0) >= Forecast.REFRESH_MS;
        boolean mayTry = !same || now - last.getLong("tried", 0) >= Forecast.RETRY_MS;
        if (stale && mayTry && fetching.compareAndSet(false, true)) {
            last.edit().putLong("tried", now).apply();
            io.execute(() -> {
                try {
                    double[] reading = Forecast.now(latitude, longitude);
                    last.edit().putString("where", where).putInt("temperature", (int) Math.round(reading[0]))
                            .putInt("code", (int) reading[1]).putBoolean("day", reading[2] == 1)
                            .putLong("fetched", System.currentTimeMillis()).apply();
                    getContext().getContentResolver().notifyChange(BASE, null);
                } catch (Exception ignored) {
                    // Offline or a bad answer: the last reading stays, and the next query tries again after RETRY_MS.
                } finally {
                    fetching.set(false);
                }
            });
        }
        if (same && last.contains("temperature")) {
            rows.addRow(new Object[]{last.getInt("temperature", 0), icon(last.getInt("code", 3), last.getBoolean("day", true))});
        }
        return rows;
    }

    /** The outline drawable for a WMO weather code. */
    static String icon(int code, boolean day) {
        if (code <= 1) return day ? "wx_sun" : "wx_moon";
        if (code <= 3) return "wx_cloud";
        if (code == 45 || code == 48) return "wx_fog";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "wx_snow";
        if (code >= 95) return "wx_storm";
        return "wx_rain";
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }
}
