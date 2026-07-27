/* Copyright (C) 2026 SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.ui.card;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.android.messaging.R;
import com.android.messaging.card.SmsCard;
import com.android.messaging.card.SmsCardRepository;
import com.android.messaging.ui.BugleActionBarActivity;
import com.android.messaging.util.ThreadUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only local card cache history; raw SMS bodies are deliberately not displayed here. */
public final class SmsCardHistoryActivity extends BugleActionBarActivity {
 private final ExecutorService mWorker=Executors.newSingleThreadExecutor(); private HistoryAdapter mAdapter;
 @Override protected void onCreate(Bundle state){super.onCreate(state);ActionBar bar=getSupportActionBar();if(bar!=null){bar.setDisplayHomeAsUpEnabled(true);bar.setTitle(R.string.sms_card_history_title);}RecyclerView list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));mAdapter=new HistoryAdapter();list.setAdapter(mAdapter);setContentView(list);mWorker.execute(()->{List<SmsCard> cards=SmsCardRepository.getHistory();ThreadUtil.getMainThreadHandler().post(()->mAdapter.set(cards));});}
 @Override public boolean onSupportNavigateUp(){finish();return true;} @Override protected void onDestroy(){mWorker.shutdownNow();super.onDestroy();}
 private static final class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder>{private final ArrayList<SmsCard> cards=new ArrayList<>();void set(List<SmsCard> values){cards.clear();cards.addAll(values);notifyDataSetChanged();}@NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){TextView v=new TextView(parent.getContext());v.setPadding(32,24,32,24);return new Holder(v);}@Override public void onBindViewHolder(@NonNull Holder h,int pos){SmsCard c=cards.get(pos);h.text.setText(c.cardType.name()+"  ·  "+Math.round(c.confidence*100)+"%");}@Override public int getItemCount(){return cards.size();}static final class Holder extends RecyclerView.ViewHolder{final TextView text;Holder(TextView v){super(v);text=v;}}}
}
