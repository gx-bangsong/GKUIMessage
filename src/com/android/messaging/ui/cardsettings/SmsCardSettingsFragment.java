/* Copyright (C) 2026 SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.ui.cardsettings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.messaging.R;
import com.android.messaging.card.CardType;
import com.android.messaging.card.SmsCardRepository;
import com.android.messaging.card.SmsCardRuleStore;
import com.android.messaging.card.SmsCardSettings;
import com.android.messaging.ui.card.SmsCardHistoryActivity;
import com.android.messaging.util.BuglePrefs;
import com.android.messaging.util.ThreadUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local-only settings for card parsing, rule import/export, cache history, and type toggles. */
public final class SmsCardSettingsFragment extends Fragment {
 private final ExecutorService mWorker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"SmsCardSettings");t.setPriority(Thread.MIN_PRIORITY);return t;});
 private SharedPreferences mPrefs;
 private final ActivityResultLauncher<String> mImport=registerForActivityResult(new ActivityResultContracts.GetContent(), this::importRule);
 private final ActivityResultLauncher<String> mExport=registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), this::exportRules);
 @Nullable @Override public View onCreateView(@NonNull LayoutInflater i,@Nullable ViewGroup c,@Nullable Bundle s){return i.inflate(R.layout.sms_card_settings_fragment,c,false);}
 @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state){super.onViewCreated(view,state);
  mPrefs=requireContext().getSharedPreferences(BuglePrefs.SHARED_PREFERENCES_NAME,Context.MODE_PRIVATE);
  MaterialSwitch enabled=view.findViewById(R.id.sms_card_enabled); enabled.setChecked(SmsCardSettings.isEnabled(requireContext()));
  enabled.setOnCheckedChangeListener((b,v)->mPrefs.edit().putBoolean(SmsCardSettings.ENABLED,v).apply());
  MaterialSwitch external=view.findViewById(R.id.sms_card_external_access); external.setChecked(mPrefs.getBoolean(SmsCardSettings.EXTERNAL_ACCESS,false));
  external.setOnCheckedChangeListener((b,v)->{if(v)new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.sms_card_external_title).setMessage(R.string.sms_card_external_message).setPositiveButton(android.R.string.ok,null).show();mPrefs.edit().putBoolean(SmsCardSettings.EXTERNAL_ACCESS,v).apply();});
  Slider confidence=view.findViewById(R.id.sms_card_confidence); confidence.setValue(SmsCardSettings.minimumConfidence(requireContext()));
  confidence.addOnChangeListener((slider,value,fromUser)->mPrefs.edit().putFloat(SmsCardSettings.MIN_CONFIDENCE,value).apply());
  LinearLayout types=view.findViewById(R.id.sms_card_type_container); for(CardType type:CardType.values()){MaterialSwitch sw=new MaterialSwitch(requireContext());sw.setText(type.name().replace("_CARD",""));sw.setChecked(SmsCardSettings.isTypeEnabled(requireContext(),type));sw.setOnCheckedChangeListener((b,v)->SmsCardSettings.setTypeEnabled(requireContext(),type,v));types.addView(sw);}
  view.findViewById(R.id.sms_card_history).setOnClickListener(v->startActivity(new Intent(requireContext(),SmsCardHistoryActivity.class)));
  view.findViewById(R.id.sms_card_clear).setOnClickListener(v->new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.sms_card_clear_title).setMessage(R.string.sms_card_clear_message).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.sms_card_clear_action,(d,w)->mWorker.execute(()->SmsCardRepository.clearAll())).show());
  view.findViewById(R.id.sms_card_import).setOnClickListener(v->mImport.launch("application/json"));
  view.findViewById(R.id.sms_card_export).setOnClickListener(v->mExport.launch("sms_card_rules.json"));
 }
 private void importRule(@Nullable Uri uri){if(uri==null)return;mWorker.execute(()->{try(InputStream in=requireContext().getContentResolver().openInputStream(uri)){SmsCardRuleStore.importRule(requireContext(),in,"imported_"+System.currentTimeMillis()+".json");toast(R.string.sms_card_import_success);}catch(Exception e){toast(R.string.sms_card_import_failed);}});}
 private void exportRules(@Nullable Uri uri){if(uri==null)return;mWorker.execute(()->{try(OutputStream out=requireContext().getContentResolver().openOutputStream(uri)){out.write(SmsCardRuleStore.exportRules(requireContext()).getBytes(java.nio.charset.StandardCharsets.UTF_8));toast(R.string.sms_card_export_success);}catch(Exception e){toast(R.string.sms_card_export_failed);}});}
 private void toast(int id){ThreadUtil.getMainThreadHandler().post(()->{if(isAdded())Toast.makeText(requireContext(),id,Toast.LENGTH_SHORT).show();});}
 @Override public void onDestroy(){mWorker.shutdownNow();super.onDestroy();}
}
