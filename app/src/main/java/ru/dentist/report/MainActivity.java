package ru.dentist.report;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private final String[] modes = {"oms","pmu","paid"};
    private final String[] modeTitles = {"ОМС","ПМУ","Платные"};
    private int mode = 0;
    private LinearLayout root, form, list, reportBox;
    private TextView title, monthTitle, totals;
    private EditText date, a,b,c,d,e,f;
    private SharedPreferences prefs;
    private final DecimalFormat money = new DecimalFormat("#,##0.00");

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs=getSharedPreferences("dentist-report",MODE_PRIVATE);
        buildUi();
        refresh();
    }

    private TextView tv(String s,int sp,boolean bold){
        TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.rgb(23,32,51));
        if(bold) v.setTypeface(null,1); v.setPadding(0,8,0,8); return v;
    }
    private Button btn(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); return b; }
    private EditText input(String hint){ EditText x=new EditText(this); x.setHint(hint); x.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); x.setSingleLine(true); return x; }

    private void buildUi(){
        ScrollView sc=new ScrollView(this); root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,20,24,40); sc.addView(root); setContentView(sc);
        title=tv("Отчёт стоматолога",25,true); root.addView(title);
        TextView sub=tv("Ежедневный ввод · месячные итоги · печать PDF",14,false); sub.setTextColor(Color.GRAY); root.addView(sub);
        LinearLayout tabs=new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL);
        for(int i=0;i<3;i++){ final int m=i; Button x=btn(modeTitles[i]); x.setOnClickListener(v->{mode=m; clearForm(); refresh();}); tabs.addView(x,new LinearLayout.LayoutParams(0,-2,1)); }
        root.addView(tabs);
        monthTitle=tv("",18,true); root.addView(monthTitle);
        totals=tv("",15,false); root.addView(totals);
        form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); root.addView(form);
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button save=btn("Сохранить день"); save.setOnClickListener(v->saveDay()); actions.addView(save,new LinearLayout.LayoutParams(0,-2,1));
        Button del=btn("Удалить день"); del.setOnClickListener(v->deleteDay()); actions.addView(del,new LinearLayout.LayoutParams(0,-2,1)); root.addView(actions);
        list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(tv("Дни месяца",18,true)); root.addView(list);
        reportBox=new LinearLayout(this); reportBox.setOrientation(LinearLayout.VERTICAL); root.addView(tv("Отчёты",18,true)); root.addView(reportBox);
    }

    private void refresh(){
        form.removeAllViews(); list.removeAllViews(); reportBox.removeAllViews();
        title.setText("Отчёт стоматолога · "+modeTitles[mode]);
        date=new EditText(this); date.setHint("Дата"); date.setFocusable(false); date.setText(today()); date.setOnClickListener(v->pickDate()); form.addView(date);
        if(mode<2){ a=input("Принято больных всего"); b=input("Принято первичных больных всего"); c=input("Удалено зубов"); d=input("Проведено операций"); e=input("УЕТ"); form.addView(a);form.addView(b);form.addView(c);form.addView(d);form.addView(e); }
        else { a=input("Всего посещений"); b=input("Анестезией всего"); c=input("Инфильтрационная анестезия"); d=input("Удаление"); e=input("Хирургическая операция"); f=input("Сумма, ₽"); form.addView(a);form.addView(b);form.addView(c);form.addView(d);form.addView(e);form.addView(f); }
        date.setOnLongClickListener(v->{loadCurrentDate();return true;});
        showMonth();
        Button pr=btn("Открыть "+modeTitles[mode]+" для печати / PDF"); pr.setOnClickListener(v->printCurrent()); reportBox.addView(pr);
        if(mode!=0){ Button all=btn("Сформировать все 3 отчёта по очереди"); all.setOnClickListener(v->Toast.makeText(this,"Откройте каждый раздел и нажмите печать",Toast.LENGTH_LONG).show()); reportBox.addView(all); }
    }

    private String today(){ return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date()); }
    private String monthKey(){ String s=date==null?today():date.getText().toString(); return s.length()>=7?s.substring(0,7):today().substring(0,7); }
    private void pickDate(){ Calendar c=Calendar.getInstance(); new DatePickerDialog(this,(v,y,m,d)->{date.setText(String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d)); loadCurrentDate(); showMonth();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show(); }
    private double num(EditText x){ try{return Double.parseDouble(x.getText().toString().replace(',','.'));}catch(Exception e){return 0;} }
    private String n(double x){ if(Math.abs(x-Math.rint(x))<0.0001) return String.valueOf((long)Math.rint(x)); return new DecimalFormat("0.##").format(x); }

    private JSONObject data(){ try{return new JSONObject(prefs.getString("data","{}"));}catch(Exception e){return new JSONObject();} }
    private JSONObject modeData(JSONObject all){ JSONObject x=all.optJSONObject(modes[mode]); if(x==null){x=new JSONObject(); try{all.put(modes[mode],x);}catch(Exception ignored){}} return x; }
    private void persist(JSONObject all){ prefs.edit().putString("data",all.toString()).apply(); }

    private void saveDay(){ try{
        JSONObject all=data(), md=modeData(all), r=new JSONObject(); String dt=date.getText().toString();
        if(mode<2){r.put("patients",num(a));r.put("primary",num(b));r.put("extractions",num(c));r.put("operations",num(d));r.put("uet",num(e));}
        else {r.put("visits",num(a));r.put("anesthesia",num(b));r.put("infiltration",num(c));r.put("extractions",num(d));r.put("operations",num(e));r.put("amount",num(f));}
        md.put(dt,r); persist(all); Toast.makeText(this,"Сохранено",Toast.LENGTH_SHORT).show(); showMonth();
    }catch(Exception ex){Toast.makeText(this,"Ошибка сохранения",Toast.LENGTH_SHORT).show();}}
    private void deleteDay(){ try{JSONObject all=data(),md=modeData(all);md.remove(date.getText().toString());persist(all);clearFields();showMonth();}catch(Exception ignored){} }
    private void clearForm(){ if(date!=null) date.setText(today()); clearFields(); }
    private void clearFields(){ EditText[] xs={a,b,c,d,e,f}; for(EditText x:xs) if(x!=null)x.setText(""); }
    private void loadCurrentDate(){ try{JSONObject r=modeData(data()).optJSONObject(date.getText().toString()); clearFields(); if(r==null)return; if(mode<2){a.setText(n(r.optDouble("patients")));b.setText(n(r.optDouble("primary")));c.setText(n(r.optDouble("extractions")));d.setText(n(r.optDouble("operations")));e.setText(n(r.optDouble("uet")));} else {a.setText(n(r.optDouble("visits")));b.setText(n(r.optDouble("anesthesia")));c.setText(n(r.optDouble("infiltration")));d.setText(n(r.optDouble("extractions")));e.setText(n(r.optDouble("operations")));f.setText(n(r.optDouble("amount")));}}catch(Exception ignored){} }

    private List<String> datesForMonth(JSONObject md,String mk){ List<String> ds=new ArrayList<>(); Iterator<String> it=md.keys(); while(it.hasNext()){String k=it.next();if(k.startsWith(mk))ds.add(k);} Collections.sort(ds); return ds; }
    private void showMonth(){
        String mk=monthKey(); monthTitle.setText("Месяц: "+mk); list.removeAllViews(); JSONObject md=modeData(data()); List<String> ds=datesForMonth(md,mk); double[] t=new double[6];
        for(String dt:ds){ JSONObject r=md.optJSONObject(dt); if(r==null)continue; String line;
            if(mode<2){ double[] q={r.optDouble("patients"),r.optDouble("primary"),r.optDouble("extractions"),r.optDouble("operations"),r.optDouble("uet")}; for(int i=0;i<5;i++)t[i]+=q[i]; line=dt+"  · всего "+n(q[0])+" · перв. "+n(q[1])+" · удал. "+n(q[2])+" · опер. "+n(q[3])+" · УЕТ "+n(q[4]); }
            else { double[] q={r.optDouble("visits"),r.optDouble("anesthesia"),r.optDouble("infiltration"),r.optDouble("extractions"),r.optDouble("operations"),r.optDouble("amount")}; for(int i=0;i<6;i++)t[i]+=q[i]; line=dt+" · посещ. "+n(q[0])+" · анест. "+n(q[1])+" · инф. "+n(q[2])+" · удал. "+n(q[3])+" · опер. "+n(q[4])+" · ₽ "+money.format(q[5]); }
            TextView row=tv(line,14,false); row.setPadding(12,12,12,12); row.setBackgroundColor(Color.WHITE); row.setOnClickListener(v->{String x=((TextView)v).getText().toString().substring(0,10);date.setText(x);loadCurrentDate();}); list.addView(row);
        }
        if(mode<2) totals.setText("ИТОГО · всего "+n(t[0])+" · первичных "+n(t[1])+" · удалено "+n(t[2])+" · операций "+n(t[3])+" · УЕТ "+n(t[4]));
        else totals.setText("ИТОГО · посещений "+n(t[0])+" · анестезий "+n(t[1])+" · инфильтр. "+n(t[2])+" · удалений "+n(t[3])+" · операций "+n(t[4])+" · сумма "+money.format(t[5])+" ₽");
    }

    @SuppressLint("SetJavaScriptEnabled") private void printCurrent(){
        final WebView w=new WebView(this); w.getSettings().setJavaScriptEnabled(true); root.addView(w,new LinearLayout.LayoutParams(2,2));
        w.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView view,String url){ PrintManager pm=(PrintManager)getSystemService(Context.PRINT_SERVICE); String name="Отчёт_"+modeTitles[mode]+"_"+monthKey(); pm.print(name,w.createPrintDocumentAdapter(name),new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build()); }});
        w.loadDataWithBaseURL(null,buildHtml(),"text/html","UTF-8",null);
    }

    private String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private String buildHtml(){ return mode<2?html039():htmlPaid(); }
    private String html039(){ JSONObject md=modeData(data()); List<String> ds=datesForMonth(md,monthKey()); StringBuilder rows=new StringBuilder(); double[] t=new double[5];
        for(String dt:ds){JSONObject r=md.optJSONObject(dt);double p=r.optDouble("patients"),pr=r.optDouble("primary"),ex=r.optDouble("extractions"),op=r.optDouble("operations"),u=r.optDouble("uet");t[0]+=p;t[1]+=pr;t[2]+=ex;t[3]+=op;t[4]+=u; String[] v={dt.substring(8,10),n(p),n(pr),"","","","","","","","","",n(ex),"",n(op),"","","","","",n(u),""}; rows.append("<tr>");for(String x:v)rows.append("<td>").append(x).append("</td>");rows.append("</tr>");}
        String[] heads={"Дата","Принято больных ВСЕГО","Первичных ВСЕГО","Дети","Запломбировано ВСЕГО","Кариес пост.","Кариес мол.","Осложн. пост.","Осложн. мол.","1 посещение","Пародонт","Слизистая","Удалено зубов","Пародонт удал.","Операции","Санировано","Осмотрено","Нуждалось","Нуждались","Профилактика","УЕТ","Беременные"}; StringBuilder h=new StringBuilder(); for(int i=0;i<heads.length;i++)h.append("<th>").append(heads[i]).append("<br><b>").append(i+1).append("</b></th>");
        return css()+"<h2>СВОДНАЯ ВЕДОМОСТЬ · "+modeTitles[mode]+" · "+monthKey()+"</h2><p>учёта работы врача-стоматолога</p><table><tr>"+h+"</tr>"+rows+"</table><p><b>ИТОГО:</b> принято "+n(t[0])+"; первичных "+n(t[1])+"; удалено "+n(t[2])+"; операций "+n(t[3])+"; УЕТ "+n(t[4])+".</p>";
    }
    private String htmlPaid(){ JSONObject md=modeData(data()); List<String> ds=datesForMonth(md,monthKey()); String[] labels={"Всего посещений","Анестезией всего","Инфильтрационная","Удаление","Хирургическая операция","Сумма"}; String[] keys={"visits","anesthesia","infiltration","extractions","operations","amount"}; double[] totals=new double[6]; StringBuilder head=new StringBuilder("<th>Показатель</th>"); for(String dt:ds)head.append("<th>").append(dt.substring(8,10)).append("</th>"); head.append("<th>Итого</th>"); StringBuilder body=new StringBuilder();
        for(int i=0;i<keys.length;i++){body.append("<tr><th>").append(labels[i]).append("</th>"); for(String dt:ds){double v=md.optJSONObject(dt).optDouble(keys[i]);totals[i]+=v;body.append("<td>").append(i==5?money.format(v):n(v)).append("</td>");}body.append("<td><b>").append(i==5?money.format(totals[i]):n(totals[i])).append("</b></td></tr>");}
        return css()+"<h2>Хирургический приём (ПМУ) · "+monthKey()+"</h2><table><tr>"+head+"</tr>"+body+"</table>";
    }
    private String css(){return "<html><head><meta charset='utf-8'><style>@page{size:A4 landscape;margin:8mm}body{font-family:Arial;font-size:9px;color:#000}h2{text-align:center;font-size:16px}table{width:100%;border-collapse:collapse;table-layout:fixed}th,td{border:1px solid #000;padding:3px;text-align:center;word-break:break-word}th{font-weight:normal}p{font-size:11px}</style></head><body>";}
}
