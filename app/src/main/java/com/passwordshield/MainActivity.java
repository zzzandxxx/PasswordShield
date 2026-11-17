package com.passwordshield;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQ_EXPORT = 41;
    private static final int REQ_IMPORT = 42;

    private static final int C_OFF = Color.parseColor("#232E47");
    private static final int C_DANGER = Color.parseColor("#FF6B7A");
    private static final int C_WARN = Color.parseColor("#FFB454");
    private static final int C_ACCENT = Color.parseColor("#6D8DFF");
    private static final int C_OK = Color.parseColor("#3FD68F");
    private static final int C_MUTED = Color.parseColor("#8A94AB");

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private VaultStore store;
    private android.content.SharedPreferences prefs;
    private Runnable lockAction;
    private LayoutInflater li;

    // 门屏
    private View screenGate, screenMain, gatePw2Wrap;
    private TextView gateTitle, gateSub, gateStrLabel, gateErr, gateForgot;
    private EditText gatePw, gatePw2;
    private ImageButton gatePwEye, gatePw2Eye;
    private Button gateBtn;
    private final View[] gateBars = new View[4];
    private boolean gateSetupMode;
    private boolean gateBusy;

    // 三个页面
    private View screenGen, screenVault, screenSettings;

    // 生成页
    private TextView tvGenPw, tvLenVal, genStrLabel;
    private SeekBar seekLen;
    private Switch swLower, swUpper, swDigit, swSymbol, swAmbig;
    private final View[] genBars = new View[4];
    private String currentGenerated = "";

    // 密码库页
    private EditText etSearch;
    private TextView tvVaultInfo, tvVaultEmpty;
    private LinearLayout vaultList;
    private final Set<String> expandedIds = new HashSet<>();

    // 设置页
    private Spinner spinLock;
    private Switch swLockOnHide;

    private AlertDialog entryDialog, cpDialog;
    private VaultStore.Entry editingEntry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        li = getLayoutInflater();
        store = VaultStore.get(this);
        prefs = getSharedPreferences("pv_settings", MODE_PRIVATE);
        lockAction = () -> {
            if (store.isUnlocked()) {
                lockNow(false);
                toast(R.string.toast_auto_locked);
            }
        };
        bindViews();
        wire();
        if (store.isUnlocked()) {
            showMain();
        } else {
            showGate(store.exists());
        }
    }

    private void bindViews() {
        screenGate = findViewById(R.id.screenGate);
        screenMain = findViewById(R.id.screenMain);
        gatePw2Wrap = findViewById(R.id.gatePw2Wrap);
        gateTitle = findViewById(R.id.gateTitle);
        gateSub = findViewById(R.id.gateSub);
        gatePw = findViewById(R.id.gatePw);
        gatePwEye = findViewById(R.id.gatePwEye);
        gatePw2 = findViewById(R.id.gatePw2);
        gatePw2Eye = findViewById(R.id.gatePw2Eye);
        gateBtn = findViewById(R.id.gateBtn);
        gateErr = findViewById(R.id.gateErr);
        gateForgot = findViewById(R.id.gateForgot);
        gateStrLabel = findViewById(R.id.gateStrLabel);
        gateBars[0] = findViewById(R.id.gateStr0);
        gateBars[1] = findViewById(R.id.gateStr1);
        gateBars[2] = findViewById(R.id.gateStr2);
        gateBars[3] = findViewById(R.id.gateStr3);

        screenGen = findViewById(R.id.screenGen);
        screenVault = findViewById(R.id.screenVault);
        screenSettings = findViewById(R.id.screenSettings);

        tvGenPw = findViewById(R.id.tvGenPw);
        tvLenVal = findViewById(R.id.tvLenVal);
        genStrLabel = findViewById(R.id.genStrLabel);
        seekLen = findViewById(R.id.seekLen);
        swLower = findViewById(R.id.swLower);
        swUpper = findViewById(R.id.swUpper);
        swDigit = findViewById(R.id.swDigit);
        swSymbol = findViewById(R.id.swSymbol);
        swAmbig = findViewById(R.id.swAmbig);
        genBars[0] = findViewById(R.id.genStr0);
        genBars[1] = findViewById(R.id.genStr1);
        genBars[2] = findViewById(R.id.genStr2);
        genBars[3] = findViewById(R.id.genStr3);

        etSearch = findViewById(R.id.etSearch);
        tvVaultInfo = findViewById(R.id.tvVaultInfo);
        tvVaultEmpty = findViewById(R.id.tvVaultEmpty);
        vaultList = findViewById(R.id.vaultList);

        spinLock = findViewById(R.id.spinLock);
        swLockOnHide = findViewById(R.id.swLockOnHide);
    }

    private void wire() {
        // 门屏
        gatePw.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (gateSetupMode) setStrength(gateBars, gateStrLabel, s.toString());
            }
        });
        gatePwEye.setOnClickListener(v -> togglePwVisibility(gatePw, gatePwEye));
        gatePw2Eye.setOnClickListener(v -> togglePwVisibility(gatePw2, gatePw2Eye));
        gateBtn.setOnClickListener(v -> onGateAction());
        gateForgot.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.confirm_reset)
                    .setPositiveButton(android.R.string.ok, (d, w) -> wipeAll())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });

        // 顶栏与标签
        findViewById(R.id.btnLockNow).setOnClickListener(v -> lockNow(true));
        findViewById(R.id.tabGen).setOnClickListener(v -> switchTab(0));
        findViewById(R.id.tabVault).setOnClickListener(v -> switchTab(1));
        findViewById(R.id.tabSettings).setOnClickListener(v -> switchTab(2));

        // 生成页
        Button btnGenerate = findViewById(R.id.btnGenerate);
        Button btnSaveGen = findViewById(R.id.btnSaveGen);
        ImageButton btnCopyGen = findViewById(R.id.btnCopyGen);
        ImageButton btnRegenGen = findViewById(R.id.btnRegenGen);
        btnGenerate.setOnClickListener(v -> doGenerate());
        btnRegenGen.setOnClickListener(v -> doGenerate());
        btnCopyGen.setOnClickListener(v -> {
            if (!currentGenerated.isEmpty()) copyText(currentGenerated, R.string.copied_pw);
            else doGenerate();
        });
        tvGenPw.setOnClickListener(v -> {
            if (!currentGenerated.isEmpty()) copyText(currentGenerated, R.string.copied_pw);
        });
        btnSaveGen.setOnClickListener(v -> {
            if (currentGenerated.isEmpty()) doGenerate();
            openEntryDialog(null, currentGenerated);
        });
        seekLen.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                tvLenVal.setText(String.valueOf(p));
                if (fromUser && !currentGenerated.isEmpty()) doGenerate();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        Switch[] opts = {swLower, swUpper, swDigit, swSymbol, swAmbig};
        for (Switch sw : opts) {
            sw.setOnCheckedChangeListener((b, on) -> {
                if (!currentGenerated.isEmpty()) doGenerate();
            });
        }

        // 密码库页
        findViewById(R.id.btnAdd).setOnClickListener(v -> openEntryDialog(null, null));
        etSearch.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                renderVault();
            }
        });

        // 设置页
        ArrayAdapter<CharSequence> ad = ArrayAdapter.createFromResource(this,
                R.array.lock_options, android.R.layout.simple_spinner_item);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinLock.setAdapter(ad);
        spinLock.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                int val = pos == 0 ? 1 : pos == 1 ? 3 : pos == 2 ? 5 : pos == 3 ? 10 : 0;
                if (val != prefs.getInt("autoLockMin", 3)) {
                    prefs.edit().putInt("autoLockMin", val).apply();
                    resetLockTimer();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> p) {
            }
        });
        swLockOnHide.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean("lockOnHide", on).apply());
        findViewById(R.id.rowChangePw).setOnClickListener(v -> openChangePwDialog());
        findViewById(R.id.rowExport).setOnClickListener(v -> startExport());
        findViewById(R.id.rowImport).setOnClickListener(v -> startImport());
        findViewById(R.id.rowWipe).setOnClickListener(v -> confirmWipe());
    }

    /* ==================== 门屏（创建 / 解锁） ==================== */

    private void showGate(boolean unlockMode) {
        gateSetupMode = !unlockMode;
        screenGate.setVisibility(View.VISIBLE);
        screenMain.setVisibility(View.GONE);
        gateErr.setVisibility(View.GONE);
        if (gateSetupMode) {
            gateTitle.setText(R.string.gate_setup_title);
            gateSub.setText(R.string.gate_setup_sub);
            gatePw2Wrap.setVisibility(View.VISIBLE);
            findViewById(R.id.gateStrengthRow).setVisibility(View.VISIBLE);
            findViewById(R.id.gateWarn).setVisibility(View.VISIBLE);
            gateForgot.setVisibility(View.GONE);
            gateBtn.setText(R.string.btn_create);
            gatePw2.setText("");
            setStrength(gateBars, gateStrLabel, gatePw.getText().toString());
        } else {
            gateTitle.setText(R.string.gate_unlock_title);
            gateSub.setText(R.string.gate_unlock_sub);
            gatePw2Wrap.setVisibility(View.GONE);
            findViewById(R.id.gateStrengthRow).setVisibility(View.GONE);
            findViewById(R.id.gateWarn).setVisibility(View.GONE);
            gateForgot.setVisibility(View.VISIBLE);
            gateBtn.setText(R.string.btn_unlock);
        }
        gatePw.setText("");
        gatePw.requestFocus();
    }

    private void onGateAction() {
        if (gateBusy) return;
        final String pw = gatePw.getText().toString();
        if (gateSetupMode) {
            final String pw2 = gatePw2.getText().toString();
            if (pw.length() < 8) {
                toast(R.string.err_pw_short);
                return;
            }
            if (!pw.equals(pw2)) {
                toast(R.string.err_pw_mismatch);
                return;
            }
            setGateBusy(true);
            io.execute(() -> {
                try {
                    store.create(pw.toCharArray());
                    ui.post(() -> {
                        setGateBusy(false);
                        showMain();
                    });
                } catch (Exception e) {
                    ui.post(() -> {
                        setGateBusy(false);
                        toast("创建失败：" + e.getMessage());
                    });
                }
            });
        } else {
            if (pw.isEmpty()) {
                toast(R.string.err_pw_empty);
                return;
            }
            setGateBusy(true);
            gateErr.setVisibility(View.GONE);
            io.execute(() -> {
                try {
                    store.unlock(pw.toCharArray());
                    ui.post(() -> {
                        gatePw.getText().clear();
                        setGateBusy(false);
                        showMain();
                    });
                } catch (Exception e) {
                    ui.post(() -> {
                        setGateBusy(false);
                        gateErr.setVisibility(View.VISIBLE);
                    });
                }
            });
        }
    }

    private void setGateBusy(boolean b) {
        gateBusy = b;
        gateBtn.setEnabled(!b);
        gateBtn.setText(b ? R.string.working
                : (gateSetupMode ? R.string.btn_create : R.string.btn_unlock));
    }

    /* ==================== 锁定与主界面 ==================== */

    private void showMain() {
        screenGate.setVisibility(View.GONE);
        screenMain.setVisibility(View.VISIBLE);
        doGenerate();
        renderVault();
        syncSettingsUi();
        switchTab(0);
        resetLockTimer();
    }

    private void lockNow(boolean withToast) {
        if (entryDialog != null && entryDialog.isShowing()) entryDialog.dismiss();
        if (cpDialog != null && cpDialog.isShowing()) cpDialog.dismiss();
        store.lock();
        expandedIds.clear();
        showGate(true);
        if (withToast) toast(R.string.toast_locked);
    }

    private void resetLockTimer() {
        ui.removeCallbacks(lockAction);
        int m = prefs.getInt("autoLockMin", 3);
        if (m > 0) ui.postDelayed(lockAction, m * 60_000L);
    }

    @Override
    public void onUserInteraction() {
        if (store.isUnlocked()) resetLockTimer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (store.isUnlocked()) resetLockTimer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        ui.removeCallbacks(lockAction);
        if (prefs.getBoolean("lockOnHide", false) && store.isUnlocked()) lockNow(true);
    }

    private void switchTab(int which) {
        screenGen.setVisibility(which == 0 ? View.VISIBLE : View.GONE);
        screenVault.setVisibility(which == 1 ? View.VISIBLE : View.GONE);
        screenSettings.setVisibility(which == 2 ? View.VISIBLE : View.GONE);
        int[][] map = {
                {R.id.tabGenIc, R.id.tabGenTxt},
                {R.id.tabVaultIc, R.id.tabVaultTxt},
                {R.id.tabSettingsIc, R.id.tabSettingsTxt}
        };
        for (int i = 0; i < 3; i++) {
            ImageView ic = findViewById(map[i][0]);
            TextView tx = findViewById(map[i][1]);
            int color = (i == which) ? C_ACCENT : C_MUTED;
            ic.setColorFilter(color);
            tx.setTextColor(color);
        }
    }

    /* ==================== 生成页 ==================== */

    private void doGenerate() {
        int len = seekLen.getProgress();
        boolean any = swLower.isChecked() || swUpper.isChecked()
                || swDigit.isChecked() || swSymbol.isChecked();
        if (!any) {
            toast(R.string.err_need_charset);
            return;
        }
        currentGenerated = PwGen.generate(len,
                swLower.isChecked(), swUpper.isChecked(),
                swDigit.isChecked(), swSymbol.isChecked(), swAmbig.isChecked());
        if (currentGenerated == null) currentGenerated = "";
        tvGenPw.setText(currentGenerated.isEmpty()
                ? getString(R.string.gen_placeholder) : currentGenerated);
        setStrength(genBars, genStrLabel, currentGenerated);
    }

    private void setStrength(View[] bars, TextView label, String pw) {
        int level = PwGen.strengthLevel(pw);
        int bits = PwGen.entropyBits(pw);
        int color;
        String word;
        switch (level) {
            case 1:
                color = C_DANGER;
                word = getString(R.string.strength_weak);
                break;
            case 2:
                color = C_WARN;
                word = getString(R.string.strength_medium);
                break;
            case 3:
                color = C_ACCENT;
                word = getString(R.string.strength_strong);
                break;
            case 4:
                color = C_OK;
                word = getString(R.string.strength_very);
                break;
            default:
                color = C_MUTED;
                word = "—";
        }
        for (int i = 0; i < bars.length; i++) {
            bars[i].setBackgroundColor(i < level ? color : C_OFF);
        }
        label.setText(pw == null || pw.isEmpty() ? "—"
                : getString(R.string.entropy_fmt, word, bits));
    }

    private void togglePwVisibility(EditText et, ImageButton btn) {
        boolean masked = et.getTransformationMethod() != null;
        et.setTransformationMethod(masked ? null : PasswordTransformationMethod.getInstance());
        et.setSelection(et.getText().length());
        btn.setImageResource(masked ? R.drawable.ic_eye_off : R.drawable.ic_eye);
    }

    /* ==================== 密码库页 ==================== */

    private void renderVault() {
        vaultList.removeAllViews();
        List<VaultStore.Entry> all = new ArrayList<>(store.entries);
        Collections.sort(all, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        String q = etSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<VaultStore.Entry> shown = new ArrayList<>();
        for (VaultStore.Entry e : all) {
            if (q.isEmpty() || contains(e.title, q) || contains(e.username, q)
                    || contains(e.url, q) || contains(e.notes, q)) {
                shown.add(e);
            }
        }
        tvVaultInfo.setText(getString(R.string.vault_count, store.entries.size()));
        boolean empty = store.entries.isEmpty();
        tvVaultEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        for (VaultStore.Entry e : shown) vaultList.addView(buildEntryView(e));
        if (!empty && shown.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(R.string.no_match);
            tv.setTextColor(C_MUTED);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, dp(30), 0, dp(30));
            vaultList.addView(tv);
        }
    }

    private View buildEntryView(VaultStore.Entry e) {
        View v = li.inflate(R.layout.item_entry, vaultList, false);
        TextView avatar = v.findViewById(R.id.entryAvatar);
        TextView title = v.findViewById(R.id.entryTitle);
        TextView sub = v.findViewById(R.id.entrySub);
        TextView chip = v.findViewById(R.id.entryChip);
        ImageView chev = v.findViewById(R.id.entryChev);
        View head = v.findViewById(R.id.entryHead);
        LinearLayout body = v.findViewById(R.id.entryBody);
        TextView pwTv = v.findViewById(R.id.entryPw);
        ImageButton eye = v.findViewById(R.id.entryEye);
        ImageButton copyPw = v.findViewById(R.id.entryCopyPw);
        View rowUser = v.findViewById(R.id.entryRowUser);
        TextView userTv = v.findViewById(R.id.entryUser);
        ImageButton copyUser = v.findViewById(R.id.entryCopyUser);
        View rowUrl = v.findViewById(R.id.entryRowUrl);
        TextView urlTv = v.findViewById(R.id.entryUrl);
        View rowNotes = v.findViewById(R.id.entryRowNotes);
        TextView notesTv = v.findViewById(R.id.entryNotes);
        Button edit = v.findViewById(R.id.entryEdit);
        Button del = v.findViewById(R.id.entryDelete);

        String t = e.title == null ? "" : e.title;
        avatar.setText(t.isEmpty() ? "?" : t.substring(0, 1).toUpperCase(Locale.ROOT));
        title.setText(t);
        String subText = joinNonEmpty(" · ", e.username,
                e.url == null ? null : e.url.replaceFirst("^https?://", ""));
        sub.setText(subText);
        sub.setVisibility(subText.isEmpty() ? View.GONE : View.VISIBLE);

        int level = PwGen.strengthLevel(e.password);
        int color;
        String word;
        switch (level) {
            case 1:
                color = C_DANGER;
                word = getString(R.string.strength_weak);
                break;
            case 2:
                color = C_WARN;
                word = getString(R.string.strength_medium);
                break;
            case 3:
                color = C_ACCENT;
                word = getString(R.string.strength_strong);
                break;
            case 4:
                color = C_OK;
                word = getString(R.string.strength_very);
                break;
            default:
                color = C_MUTED;
                word = "—";
        }
        chip.setText(word);
        chip.setTextColor(color);
        ((GradientDrawable) chip.getBackground().mutate()).setColor(0x33000000 | (color & 0xFFFFFF));

        boolean open = expandedIds.contains(e.id);
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        chev.setRotation(open ? 180 : 0);
        head.setOnClickListener(x -> {
            boolean nowOpen = expandedIds.contains(e.id);
            if (nowOpen) expandedIds.remove(e.id);
            else expandedIds.add(e.id);
            body.setVisibility(!nowOpen ? View.VISIBLE : View.GONE);
            chev.setRotation(!nowOpen ? 180 : 0);
        });

        final boolean[] revealed = {false};
        Runnable refreshPw = () -> {
            int n = Math.min(e.password == null ? 0 : e.password.length(), 12);
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < Math.max(n, 6); i++) dots.append('•');
            pwTv.setText(revealed[0] ? e.password : dots.toString());
            eye.setImageResource(revealed[0] ? R.drawable.ic_eye_off : R.drawable.ic_eye);
        };
        refreshPw.run();
        eye.setOnClickListener(x -> {
            revealed[0] = !revealed[0];
            refreshPw.run();
        });
        copyPw.setOnClickListener(x -> copyText(e.password, R.string.copied_pw));

        if (e.username != null && !e.username.isEmpty()) {
            rowUser.setVisibility(View.VISIBLE);
            userTv.setText(e.username);
            copyUser.setOnClickListener(x -> copyText(e.username, R.string.copied_user));
        }
        if (e.url != null && !e.url.isEmpty()) {
            rowUrl.setVisibility(View.VISIBLE);
            urlTv.setText(e.url);
            urlTv.setOnClickListener(x -> {
                try {
                    String u = e.url.startsWith("http") ? e.url : "https://" + e.url;
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
                } catch (Exception ignored) {
                }
            });
        }
        if (e.notes != null && !e.notes.isEmpty()) {
            rowNotes.setVisibility(View.VISIBLE);
            notesTv.setText(e.notes);
        }
        edit.setOnClickListener(x -> openEntryDialog(e, null));
        del.setOnClickListener(x -> new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(getString(R.string.confirm_delete_msg, t))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    io.execute(() -> {
                        synchronized (store) {
                            store.entries.remove(e);
                            try {
                                store.persist();
                            } catch (Exception ignored) {
                            }
                        }
                        ui.post(this::renderVault);
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        return v;
    }

    /* ==================== 添加 / 编辑弹窗 ==================== */

    private void openEntryDialog(VaultStore.Entry entry, String presetPw) {
        editingEntry = entry;
        View dv = li.inflate(R.layout.dialog_entry, null);
        final EditText fTitle = dv.findViewById(R.id.dlgTitle);
        final EditText fUser = dv.findViewById(R.id.dlgUser);
        final EditText fPw = dv.findViewById(R.id.dlgPw);
        final EditText fUrl = dv.findViewById(R.id.dlgUrl);
        final EditText fNotes = dv.findViewById(R.id.dlgNotes);
        ImageButton bEye = dv.findViewById(R.id.dlgEye);
        ImageButton bGen = dv.findViewById(R.id.dlgGen);
        TextView sLabel = dv.findViewById(R.id.dlgStrLabel);
        View[] sBars = {dv.findViewById(R.id.dlgStr0), dv.findViewById(R.id.dlgStr1),
                dv.findViewById(R.id.dlgStr2), dv.findViewById(R.id.dlgStr3)};

        if (entry != null) {
            fTitle.setText(entry.title);
            fUser.setText(entry.username);
            fPw.setText(entry.password);
            fUrl.setText(entry.url);
            fNotes.setText(entry.notes);
        } else if (presetPw != null) {
            fPw.setText(presetPw);
        }
        setStrength(sBars, sLabel, fPw.getText().toString());
        fPw.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                setStrength(sBars, sLabel, s.toString());
            }
        });
        bEye.setOnClickListener(v -> togglePwVisibility(fPw, bEye));
        bGen.setOnClickListener(v -> {
            String p = PwGen.generate(seekLen.getProgress(),
                    swLower.isChecked(), swUpper.isChecked(),
                    swDigit.isChecked(), swSymbol.isChecked(), swAmbig.isChecked());
            if (p != null) {
                fPw.setText(p);
                fPw.setSelection(p.length());
            }
        });

        entryDialog = new AlertDialog.Builder(this)
                .setTitle(entry == null ? R.string.dlg_add_title : R.string.dlg_edit_title)
                .setView(dv)
                .setPositiveButton(R.string.btn_save, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        entryDialog.show();
        entryDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String t = fTitle.getText().toString().trim();
            String p = fPw.getText().toString();
            if (t.isEmpty()) {
                toast(R.string.err_title_required);
                return;
            }
            if (p.isEmpty()) {
                toast(R.string.err_pw_required);
                return;
            }
            final String title = t;
            final String password = p;
            final String user = fUser.getText().toString().trim();
            final String url = fUrl.getText().toString().trim();
            final String notes = fNotes.getText().toString().trim();
            final boolean isUpdate = editingEntry != null;
            io.execute(() -> {
                try {
                    synchronized (store) {
                        long now = System.currentTimeMillis();
                        if (isUpdate) {
                            VaultStore.Entry e = editingEntry;
                            e.title = title;
                            e.username = user;
                            e.password = password;
                            e.url = url;
                            e.notes = notes;
                            e.updatedAt = now;
                        } else {
                            VaultStore.Entry e = new VaultStore.Entry();
                            e.id = UUID.randomUUID().toString();
                            e.title = title;
                            e.username = user;
                            e.password = password;
                            e.url = url;
                            e.notes = notes;
                            e.createdAt = now;
                            e.updatedAt = now;
                            store.entries.add(e);
                        }
                        store.persist();
                    }
                    ui.post(() -> {
                        renderVault();
                        toast(isUpdate ? R.string.toast_updated : R.string.toast_saved);
                    });
                } catch (Exception ex) {
                    ui.post(() -> toast("保存失败：" + ex.getMessage()));
                }
            });
            entryDialog.dismiss();
        });
    }

    /* ==================== 设置页 ==================== */

    private void syncSettingsUi() {
        int m = prefs.getInt("autoLockMin", 3);
        spinLock.setSelection(m == 1 ? 0 : m == 3 ? 1 : m == 5 ? 2 : m == 10 ? 3 : 4, false);
        swLockOnHide.setChecked(prefs.getBoolean("lockOnHide", false));
    }

    private void openChangePwDialog() {
        View dv = li.inflate(R.layout.dialog_changepw, null);
        final EditText cur = dv.findViewById(R.id.cpCur);
        final EditText nw = dv.findViewById(R.id.cpNew);
        final EditText nw2 = dv.findViewById(R.id.cpNew2);
        TextView sLabel = dv.findViewById(R.id.cpStrLabel);
        View[] sBars = {dv.findViewById(R.id.cpStr0), dv.findViewById(R.id.cpStr1),
                dv.findViewById(R.id.cpStr2), dv.findViewById(R.id.cpStr3)};
        nw.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                setStrength(sBars, sLabel, s.toString());
            }
        });
        cpDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dlg_cp_title)
                .setView(dv)
                .setPositiveButton(R.string.btn_save, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        cpDialog.show();
        cpDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String c = cur.getText().toString();
            String n1 = nw.getText().toString();
            String n2 = nw2.getText().toString();
            if (n1.length() < 8) {
                toast(R.string.err_pw_short);
                return;
            }
            if (!n1.equals(n2)) {
                toast(R.string.err_pw_mismatch);
                return;
            }
            io.execute(() -> {
                try {
                    synchronized (store) {
                        store.rekey(c.toCharArray(), n1.toCharArray());
                    }
                    ui.post(() -> toast(R.string.toast_pw_changed));
                } catch (Exception e) {
                    ui.post(() -> toast(R.string.err_cur_pw));
                }
            });
            cpDialog.dismiss();
        });
    }

    private void startExport() {
        String name = "密码盾备份-"
                + new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date()) + ".json";
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(i, REQ_EXPORT);
        } catch (Exception e) {
            toast(R.string.toast_export_fail);
        }
    }

    private void startImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            toast(R.string.toast_import_fail);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (req == REQ_EXPORT) {
            io.execute(() -> {
                try {
                    String json;
                    synchronized (store) {
                        json = store.recordJson();
                    }
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os == null) throw new Exception("no stream");
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                    os.close();
                    ui.post(() -> toast(R.string.toast_export_ok));
                } catch (Exception e) {
                    ui.post(() -> toast(R.string.toast_export_fail));
                }
            });
        } else if (req == REQ_IMPORT) {
            io.execute(() -> {
                try {
                    InputStream in = getContentResolver().openInputStream(uri);
                    if (in == null) throw new Exception("no stream");
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    in.close();
                    final String json = new String(bo.toByteArray(), StandardCharsets.UTF_8);
                    JSONObject rec = new JSONObject(json);
                    if (!rec.has("salt") || !rec.has("iv") || !rec.has("ct") || !rec.has("iter")) {
                        throw new Exception("bad");
                    }
                    final int cur = store.entries.size();
                    ui.post(() -> new AlertDialog.Builder(this)
                            .setMessage(getString(R.string.import_confirm, cur))
                            .setPositiveButton(android.R.string.ok, (d, w) -> {
                                io.execute(() -> {
                                    try {
                                        synchronized (store) {
                                            store.replaceRecord(json);
                                        }
                                        ui.post(() -> {
                                            showGate(true);
                                            toast(R.string.toast_import_ok);
                                        });
                                    } catch (Exception ex) {
                                        ui.post(() -> toast(R.string.toast_import_fail));
                                    }
                                });
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show());
                } catch (Exception e) {
                    ui.post(() -> toast(R.string.toast_import_fail));
                }
            });
        }
    }

    private void confirmWipe() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.confirm_wipe_1)
                .setPositiveButton(android.R.string.ok, (d, w) -> new AlertDialog.Builder(this)
                        .setMessage(R.string.confirm_wipe_2)
                        .setPositiveButton(android.R.string.ok, (d2, w2) -> wipeAll())
                        .setNegativeButton(android.R.string.cancel, null)
                        .show())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void wipeAll() {
        io.execute(() -> {
            synchronized (store) {
                store.reset();
            }
            ui.post(() -> {
                showGate(false);
                toast(R.string.toast_wiped);
            });
        });
    }

    /* ==================== 工具 ==================== */

    private void copyText(String text, int msgRes) {
        if (text == null || text.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("password", text);
        if (Build.VERSION.SDK_INT >= 33) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }
        cm.setPrimaryClip(clip);
        toast(msgRes);
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
    }

    private static String joinNonEmpty(String sep, String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (b.length() > 0) b.append(sep);
            b.append(p);
        }
        return b.toString();
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {
        }

        @Override
        public void onTextChanged(CharSequence s, int a, int b, int c) {
        }
    }
}
