package ec.espoch.monitoreoambiental;

import android.Manifest;
import android.app.*;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

public class MainActivity extends Activity {
    enum Screen { HOME, HISTORY, PROCESS, SETTINGS, ALERTS }
    enum Kind { INTERNET, TEMP, HUMIDITY, POWER }
    final int BG=0xFFFFF5EB, WHITE=0xFFFFFFFF, DARK=0xFF171B17, GRAY=0xFF747B72;
    final int PALE_GREEN=0xFFE7F6D9, PEACH=0xFFFFF0E0;
    final int TEAL=0xFFFF7900, RED=0xFFFF5227, BLUE=0xFF168A0B, GREEN=0xFF168A0B;
    boolean processStages=false, historyTable=false, dateDirty=false;
    LocalIncubationData data;
    ScrollView currentScroll;
    Screen renderedScreen;
    Screen screen=Screen.HOME, previous=Screen.HOME;
    LinearLayout root, body;
    SharedPreferences prefs;
    Handler handler=new Handler(Looper.getMainLooper());
    List<String> events=new ArrayList<>();
    float temp=37.8f, hum=55, tMin=37.3f, tMax=38.3f, hMin=50, hMax=60;
    long startDate;
    boolean power=true, online=true, all=true, aInternet=true, aTemp=true, aHum=true, aPower=true;
    boolean appNote=true, phoneNote=true, sound=false;
    EditText tminInput,tmaxInput,hminInput,hmaxInput;
String period = "Semana";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(TEAL); getWindow().setNavigationBarColor(WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        prefs=getSharedPreferences("incubacion_visual",MODE_PRIVATE); if(!prefs.contains("permission_asked"))prefs.edit().putBoolean("permission_asked",getSharedPreferences("monitor",MODE_PRIVATE).getBoolean("permission_asked",false)).apply(); load(); data=new LocalIncubationData(this); makeChannel(); monitorInternet(); render(); if(Build.VERSION.SDK_INT>=33 && phoneNote && !prefs.getBoolean("permission_asked",false)){prefs.edit().putBoolean("permission_asked",true).apply();askPermission();}
    }
    void load() {
        all=prefs.getBoolean("all",true); aInternet=prefs.getBoolean("internet",true);
        aTemp=prefs.getBoolean("temp",true); aHum=prefs.getBoolean("hum",true); aPower=prefs.getBoolean("power",true);
        appNote=prefs.getBoolean("appnote",true); phoneNote=prefs.getBoolean("phonenote",true); sound=prefs.getBoolean("sound",false);
        Calendar initial=Calendar.getInstance();initial.add(Calendar.DAY_OF_YEAR,-7);startDate=prefs.getLong("incubation_start",initial.getTimeInMillis());if(!prefs.contains("incubation_start"))prefs.edit().putLong("incubation_start",startDate).apply();applyStage();
    }
    void save() { prefs.edit().putBoolean("all",all).putBoolean("internet",aInternet).putBoolean("temp",aTemp)
        .putBoolean("hum",aHum).putBoolean("power",aPower).putBoolean("appnote",appNote)
        .putBoolean("phonenote",phoneNote).putBoolean("sound",sound).putFloat("tmin",tMin)
        .putFloat("tmax",tMax).putFloat("hmin",hMin).putFloat("hmax",hMax).putLong("incubation_start",startDate).apply(); }

    void render() {
        int oldScroll=currentScroll!=null&&renderedScreen==screen?currentScroll.getScrollY():0;
        applyStage();
        root=col();root.setBackgroundColor(TEAL);setContentView(root);
        root.addView(header(),new LinearLayout.LayoutParams(-1,dp(screen==Screen.HOME?137:62)));
        ScrollView scroll=new ScrollView(this);currentScroll=scroll;renderedScreen=screen;
        scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        GradientDrawable surface=round(screen==Screen.HOME?BG:WHITE,23);surface.setCornerRadii(new float[]{dp(23),dp(23),dp(23),dp(23),0,0,0,0});scroll.setBackground(surface);
        body=col();body.setPadding(dp(13),dp(screen==Screen.HOME?10:16),dp(13),dp(18));scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(screen==Screen.HOME)home();else if(screen==Screen.HISTORY)history();else if(screen==Screen.PROCESS)process();else if(screen==Screen.SETTINGS)settings();else alerts();
        LinearLayout nav=row();nav.setPadding(dp(2),dp(7),dp(2),dp(4));nav.setBackgroundColor(WHITE);nav.setElevation(dp(4));
        navItem(nav,"","Inicio",Screen.HOME);navItem(nav,"","Historial",Screen.HISTORY);navItem(nav,"","Proceso",Screen.PROCESS);navItem(nav,"","Configuración",Screen.SETTINGS);
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(65)));
        scroll.post(()->scroll.scrollTo(0,oldScroll));
    }
    void show(Screen target){screen=target;currentScroll=null;render();if(data!=null)data.refresh();}
    View header(){
        if(screen==Screen.HOME){
            FrameLayout frame=new FrameLayout(this);frame.setBackground(gradient(0xFFFF8900,0xFFFF6800,0));
            frame.addView(new HeaderWave(this),new FrameLayout.LayoutParams(-1,-1));
            LinearLayout title=col();title.addView(txt("Bienvenidos 😊",26,WHITE,true));title.addView(txt("Monitoreo de incubación avícola",12,WHITE,false),margin(-1,4,0,0));
            FrameLayout.LayoutParams labels=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP|Gravity.START);labels.setMargins(dp(20),dp(14),dp(52),0);frame.addView(title,labels);
            ImageView bell=image(R.drawable.ic_notifications,WHITE);bell.setContentDescription("Abrir alertas");bell.setPadding(dp(6),dp(6),dp(6),dp(6));bell.setOnClickListener(v->openAlerts());
            FrameLayout.LayoutParams bellPos=new FrameLayout.LayoutParams(dp(36),dp(38),Gravity.END|Gravity.TOP);bellPos.setMargins(0,dp(14),dp(12),0);frame.addView(bell,bellPos);
            ImageView hen=image(R.drawable.hen_incubacion_v2,WHITE);hen.clearColorFilter();hen.setContentDescription("Gallina con huevos");FrameLayout.LayoutParams henPos=new FrameLayout.LayoutParams(dp(128),dp(86),Gravity.END|Gravity.BOTTOM);henPos.setMargins(0,0,dp(10),0);frame.addView(hen,henPos);
            return frame;
        }
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(12),0,dp(12),0);header.setBackgroundColor(TEAL);
        TextView back=txt("‹",34,WHITE,false);back.setGravity(Gravity.CENTER);back.setContentDescription("Volver a Inicio");back.setOnClickListener(v->back());header.addView(back,new LinearLayout.LayoutParams(dp(32),dp(48)));
        String title=screen==Screen.PROCESS?"Proceso de Incubación":screen==Screen.HISTORY?"Historial de Mediciones":screen==Screen.SETTINGS?"Configuración":"Alertas";
        TextView text=txt(title,19,WHITE,true);text.setGravity(Gravity.CENTER);text.setMaxLines(1);text.setEllipsize(android.text.TextUtils.TruncateAt.END);header.addView(text,new LinearLayout.LayoutParams(0,-2,1));header.addView(new View(this),new LinearLayout.LayoutParams(dp(32),1));return header;
    }
    void navItem(LinearLayout nav,String unused,String label,Screen target){
        boolean selected=screen==target||(screen==Screen.ALERTS&&previous==target);int color=selected?TEAL:0xFF818581;
        LinearLayout item=col();item.setGravity(Gravity.CENTER);int resource=target==Screen.HOME?R.drawable.ic_home:target==Screen.HISTORY?R.drawable.ic_bars:target==Screen.PROCESS?R.drawable.ic_layers:R.drawable.ic_gear;
        item.addView(image(resource,color),new LinearLayout.LayoutParams(dp(25),dp(26)));TextView text=txt(label,10,color,selected);text.setGravity(Gravity.CENTER);text.setSingleLine(true);item.addView(text,margin(-1,4,0,0));item.setContentDescription(label);item.setOnClickListener(v->show(target));nav.addView(item,new LinearLayout.LayoutParams(0,-1,1));
    }
    int day(){Calendar a=Calendar.getInstance(),b=Calendar.getInstance();b.setTimeInMillis(startDate);for(Calendar c:new Calendar[]{a,b}){c.set(Calendar.HOUR_OF_DAY,12);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);}return Math.max(1,(int)Math.round((a.getTimeInMillis()-b.getTimeInMillis())/86400000d)+1);}
    boolean cycle(){return data!=null&&data.hasCycle;}
    boolean reading(){return data!=null&&data.displayReading();}
    org.json.JSONArray stageData(){if(data!=null&&data.stages.length()>0)return data.stages;return fallbackStages();}
    org.json.JSONArray fallbackStages(){org.json.JSONArray rows=new org.json.JSONArray();int[][] days={{1,6},{7,12},{13,17},{18,19},{20,21}};double[] temps={38,37.8,37.6,37.6,37.2},lows={60,55,60,80,80},highs={60,55,60,85,85};String[] names={"Desarrollo inicial","Desarrollo embrionario","Desarrollo avanzado","Preparación para el nacimiento","Eclosión"};try{for(int i=0;i<days.length;i++)rows.put(new org.json.JSONObject().put("dia_inicio",days[i][0]).put("dia_fin",days[i][1]).put("temperatura_objetivo",temps[i]).put("humedad_minima",lows[i]).put("humedad_maxima",highs[i]).put("nombre",names[i]));}catch(Exception ignored){}return rows;}
    org.json.JSONObject selectedStage(){org.json.JSONArray rows=stageData();for(int i=0;i<rows.length();i++){org.json.JSONObject row=rows.optJSONObject(i);if(day()>=row.optInt("dia_inicio")&&day()<=row.optInt("dia_fin"))return row;}return null;}
    float targetTemp(){org.json.JSONObject row=selectedStage();return row==null?37.2f:(float)row.optDouble("temperatura_objetivo",37.8);}
    float targetHum(){org.json.JSONObject row=selectedStage();return row==null?82.5f:(float)((row.optDouble("humedad_minima",80)+row.optDouble("humedad_maxima",85))/2);}
    void applyStage(){org.json.JSONObject row=selectedStage();float tt=data==null?.5f:data.tempTolerance,ht=data==null?5:data.humTolerance;tMin=targetTemp()-tt;tMax=targetTemp()+tt;float lo=row==null?80:(float)row.optDouble("humedad_minima",55),hi=row==null?85:(float)row.optDouble("humedad_maxima",55);hMin=lo==hi?Math.max(0,lo-ht):lo;hMax=lo==hi?Math.min(100,hi+ht):hi;}
    String n(double v){return Math.abs(v-Math.rint(v))<.001?String.format(Locale.US,"%.0f",v):String.format(Locale.US,"%.1f",v);}
    String band(float a,float b,String unit){return n(a)+" – "+n(b)+unit;}
    String stageName(){if(!cycle())return "Sin ciclo activo";org.json.JSONObject row=selectedStage();return row!=null?row.optString("nombre","Desarrollo embrionario"):day()<=23?"Observación de nacimiento":"Ciclo extendido";}
    String dayLabel(){return !cycle()?"Sin ciclo activo":day()<=21?"Día "+day()+" de 21":"Día "+day()+" · observación";}
    View incubation(boolean unused){
        LinearLayout card=card();LinearLayout first=row();first.setGravity(Gravity.CENTER_VERTICAL);first.addView(image(R.drawable.ic_calendar,TEAL),new LinearLayout.LayoutParams(dp(36),dp(38)));LinearLayout labels=col();labels.setPadding(dp(10),0,0,0);labels.addView(txt("Proceso de incubación",15,DARK,true));labels.addView(txt(dayLabel(),25,DARK,true),margin(-1,3,0,0));first.addView(labels,new LinearLayout.LayoutParams(0,-2,1));card.addView(first);
        LinearLayout progress=row();progress.setGravity(Gravity.CENTER_VERTICAL);ProgressMeter bar=new ProgressMeter(this,cycle()?Math.min(day(),21)/21f:0);progress.addView(bar,new LinearLayout.LayoutParams(0,dp(11),1));TextView percent=txt((cycle()?Math.min(100,Math.round(day()*100f/21)):0)+" %",13,DARK,false);percent.setPadding(dp(12),0,0,0);progress.addView(percent);card.addView(progress,margin(-1,10,0,0));card.addView(txt("Etapa: "+stageName(),12,DARK,false),margin(-1,5,0,0));card.setOnClickListener(v->show(Screen.PROCESS));return card;
    }
    void home(){
        add(incubation(true));LinearLayout metrics=row();metrics.addView(metric(true),weight(1,0,5));metrics.addView(metric(false),weight(1,5,0));add(metrics);
        LinearLayout first=row();first.addView(tileNew(R.drawable.ic_bars,"Historial",0xFF8751FF,()->show(Screen.HISTORY)),weight(1,0,5));first.addView(tileNew(R.drawable.ic_layers,"Proceso",TEAL,()->show(Screen.PROCESS)),weight(1,5,0));add(first);
        LinearLayout second=row();second.addView(tileNew(R.drawable.ic_notifications,"Alertas",TEAL,this::openAlerts),weight(1,0,5));second.addView(tileNew(R.drawable.ic_gear,"Configuración",GREEN,()->show(Screen.SETTINGS)),weight(1,5,0));add(second);
    }
    View metric(boolean temperature){
        LinearLayout c=card();c.setPadding(dp(9),dp(13),dp(9),dp(12));c.setGravity(Gravity.CENTER);LinearLayout label=row();label.setGravity(Gravity.CENTER);label.addView(image(temperature?R.drawable.ic_temperature:R.drawable.ic_drop,temperature?RED:GREEN),new LinearLayout.LayoutParams(dp(25),dp(31)));TextView title=txt(temperature?"Temperatura":"Humedad",13,DARK,true);title.setPadding(dp(5),0,0,0);label.addView(title);c.addView(label);
        c.addView(txt(reading()?String.format(Locale.US,temperature?"%.1f °C":"%.0f %%",temperature?temp:hum):temperature?"-- °C":"-- %",28,DARK,true),margin(-2,7,0,0));TextView range=txt("Rango: "+(cycle()?temperature?band(tMin,tMax," °C"):band(hMin,hMax," %"):"--"),11,DARK,false);range.setGravity(Gravity.CENTER);c.addView(range,margin(-1,5,0,0));
        boolean normal=reading()&&(temperature?temp>=tMin&&temp<=tMax:hum>=hMin&&hum<=hMax);String value=!reading()?"Sin lecturas":normal?"✓ Dentro del rango":"! Fuera del rango";TextView state=txt(value,10,normal?0xFF16660E:!reading()?GRAY:RED,true);state.setGravity(Gravity.CENTER);state.setPadding(dp(6),dp(7),dp(6),dp(7));state.setBackground(round(normal?PALE_GREEN:!reading()?0xFFF0F1EF:0xFFFFEBE5,12));c.addView(state,margin(-1,10,0,0));return c;
    }
    View tileNew(int resource,String name,int color,Runnable action){LinearLayout c=card();c.setGravity(Gravity.CENTER);c.setPadding(dp(10),dp(11),dp(10),dp(11));c.addView(image(resource,color),new LinearLayout.LayoutParams(dp(31),dp(31)));c.addView(txt(name,14,DARK,true),margin(-2,5,0,0));c.setMinimumHeight(dp(80));c.setContentDescription(name);c.setOnClickListener(v->action.run());return c;}
    View segments(String first,String second,boolean right,Runnable selectFirst,Runnable selectSecond){LinearLayout row=row();row.setPadding(dp(3),dp(3),dp(3),dp(3));row.setBackground(round(0xFFF0F1EF,25));String[] names={first,second};for(int i=0;i<2;i++){boolean selected=right==(i==1);TextView t=txt(names[i],13,selected?WHITE:DARK,selected);t.setGravity(Gravity.CENTER);if(selected)t.setBackground(round(GREEN,24));Runnable action=i==0?selectFirst:selectSecond;t.setOnClickListener(v->{currentScroll=null;action.run();});row.addView(t,new LinearLayout.LayoutParams(0,dp(38),i==0&&first.length()>12?1.5f:1));}return row;}
    void process(){
        add(segments("Parámetros por días","Etapas",processStages,()->{processStages=false;render();},()->{processStages=true;render();}));
        if(!processStages)add(parameterTable());else{
            String[][] timeline={{"1 – 17","Desarrollo embrionario","Monitoreo diario de temperatura y humedad."},{"18 – 19","Preparación para el nacimiento","Preparar el nacimiento y revisar los huevos según el protocolo del equipo."},{"20 – 21","Eclosión","Observar el nacimiento y mantener los pollitos hasta que se sequen."},{"22 – 23","Observación final","Seguimiento de los huevos que todavía no han eclosionado."}};
            for(int i=0;i<timeline.length;i++){LinearLayout c=card();LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);TextView badge=txt(String.valueOf(i+1),17,GREEN,true);badge.setGravity(Gravity.CENTER);badge.setBackground(round(PALE_GREEN,24));r.addView(badge,new LinearLayout.LayoutParams(dp(36),dp(36)));LinearLayout text=col();text.setPadding(dp(10),0,0,0);text.addView(txt(timeline[i][1],16,DARK,true));text.addView(txt("Días "+timeline[i][0],12,TEAL,true),margin(-1,3,0,0));r.addView(text,new LinearLayout.LayoutParams(0,-2,1));c.addView(r);c.addView(txt(timeline[i][2],13,GRAY,false),margin(-1,10,0,0));add(c);}
        }
        LinearLayout note=row();note.setPadding(dp(15),dp(16),dp(15),dp(16));note.setGravity(Gravity.TOP);note.setBackground(round(PEACH,18));note.addView(image(R.drawable.ic_info,TEAL),new LinearLayout.LayoutParams(dp(28),dp(28)));TextView message=txt("Los parámetros se actualizan automáticamente según el día del proceso.",14,DARK,false);message.setPadding(dp(10),0,0,0);note.addView(message,new LinearLayout.LayoutParams(0,-2,1));add(note);
    }
    View parameterTable(){return parameterTable(false);}
    View parameterTable(boolean compact){
        LinearLayout table=card();table.setPadding(0,0,0,0);if(compact){table.setElevation(0);table.setBackground(round(WHITE,11,0xFFEDEBE8,dp(1)));}table.setClipToOutline(true);LinearLayout head=row();head.setPadding(dp(compact?5:9),dp(compact?7:12),dp(compact?5:9),dp(compact?7:12));head.setBackgroundColor(compact?0xFFF1EFF8:PALE_GREEN);String[] titles=compact?new String[]{"Rango de días","Temperatura (°C)","Humedad (%)"}:new String[]{"Días","Temperatura\n(°C)","Humedad\n(%)"};float[] weights={.95f,1.35f,1.15f};for(int i=0;i<3;i++){TextView label=txt(titles[i],compact?9:12,DARK,true);label.setGravity(Gravity.CENTER);head.addView(label,new LinearLayout.LayoutParams(0,-2,weights[i]));}table.addView(head);
        org.json.JSONArray rows=stageData();for(int i=0;i<rows.length();i++){org.json.JSONObject row=rows.optJSONObject(i);boolean selected=cycle()&&day()>=row.optInt("dia_inicio")&&day()<=row.optInt("dia_fin");LinearLayout line=row();line.setPadding(dp(compact?5:8),dp(compact?7:13),dp(compact?5:8),dp(compact?7:13));line.setGravity(Gravity.CENTER_VERTICAL);if(selected)line.setBackground(round(compact?PEACH:0xFFF1FBE9,compact?8:0));
            LinearLayout days=row();days.setGravity(Gravity.CENTER);if(!compact)days.addView(image(R.drawable.ic_calendar,TEAL),new LinearLayout.LayoutParams(dp(18),dp(22)));TextView text=txt((compact?"Días ":"")+(row.optInt("dia_inicio")==row.optInt("dia_fin")?row.optString("dia_inicio"):row.optString("dia_inicio")+" – "+row.optString("dia_fin")),compact?11:13,DARK,true);text.setPadding(dp(compact?0:6),0,0,0);days.addView(text);line.addView(days,new LinearLayout.LayoutParams(0,-2,weights[0]));
            double t=row.optDouble("temperatura_objetivo"),lo=row.optDouble("humedad_minima"),hi=row.optDouble("humedad_maxima");float tt=data==null?.5f:data.tempTolerance,ht=data==null?5:data.humTolerance;String[] values={band((float)t-tt,(float)t+tt,""),band((float)(lo==hi?Math.max(0,lo-ht):lo),(float)(lo==hi?Math.min(100,hi+ht):hi),"")};for(int j=0;j<2;j++){TextView v=txt(values[j],compact?10:12,DARK,false);v.setGravity(Gravity.CENTER);line.addView(v,new LinearLayout.LayoutParams(0,-2,weights[j+1]));}table.addView(line);if(i<rows.length()-1)table.addView(rule(),new LinearLayout.LayoutParams(-1,dp(1)));}
        return table;
    }
    float[] historyValues(boolean temperature){return data==null?new float[0]:data.values(temperature);}
    String average(float[] unused,boolean temperature){return data==null?"--":data.average(temperature);}
    void history(){
        add(segments("Gráfica","Tabla",historyTable,()->{historyTable=false;render();},()->{historyTable=true;render();}));
        LinearLayout filter=row();filter.setGravity(Gravity.CENTER_VERTICAL);filter.setPadding(dp(12),0,dp(8),0);filter.setBackground(round(WHITE,10,0xFFDFE2DC,dp(1)));filter.addView(image(R.drawable.ic_calendar,DARK),new LinearLayout.LayoutParams(dp(21),dp(25)));
        Spinner picker=new Spinner(this);String[] names={"Hoy","Últimos 7 días","Últimos 30 días"};ArrayAdapter<String> options=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);options.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);picker.setAdapter(options);picker.setSelection(period.equals("Día")?0:period.equals("Mes")?2:1,false);filter.addView(picker,new LinearLayout.LayoutParams(0,dp(43),1));picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){String chosen=new String[]{"Día","Semana","Mes"}[position];if(!period.equals(chosen)){period=chosen;currentScroll=null;if(data!=null)data.changePeriod();render();}}});add(filter);
        float[] temperatures=historyValues(true),humidities=historyValues(false);String[] labels=new String[temperatures.length];for(int i=0;i<labels.length;i++)labels[i]=data.label(i);
        if(historyTable)add(historyRows(temperatures,humidities,labels));else{
            LinearLayout legends=row();legends.setGravity(Gravity.CENTER);legends.addView(legend("Temperatura (°C)",TEAL),new LinearLayout.LayoutParams(0,dp(32),1));legends.addView(legend("Humedad (%)",GREEN),new LinearLayout.LayoutParams(0,dp(32),1));add(legends);add(new CombinedGraph(this,temperatures,humidities,labels));
        }
        LinearLayout averages=row();averages.addView(averageCard(true),weight(1,0,5));averages.addView(averageCard(false),weight(1,5,0));add(averages);add(alertsControl());
    }
    View legend(String label,int color){LinearLayout r=row();r.setGravity(Gravity.CENTER);TextView dot=txt("●",15,color,true);r.addView(dot);TextView text=txt(label,11,DARK,false);text.setPadding(dp(5),0,0,0);r.addView(text);return r;}
    View historyRows(float[] t,float[] h,String[] labels){
        LinearLayout table=card();table.setPadding(dp(9),dp(6),dp(9),dp(6));String[] titles={"Fecha","Temp. (°C)","Hum. (%)"};LinearLayout title=row();title.setPadding(0,dp(10),0,dp(10));title.setBackground(round(PALE_GREEN,9));for(String name:titles){TextView text=txt(name,12,DARK,true);text.setGravity(Gravity.CENTER);title.addView(text,new LinearLayout.LayoutParams(0,-2,1));}table.addView(title);
        if(t.length==0){TextView empty=txt("Sin mediciones para este periodo",14,GRAY,false);empty.setGravity(Gravity.CENTER);table.addView(empty,new LinearLayout.LayoutParams(-1,dp(190)));}else for(int i=0;i<t.length;i++){LinearLayout line=row();line.setPadding(0,dp(12),0,dp(12));String[] values={labels[i],String.format(Locale.US,"%.1f",t[i]),String.format(Locale.US,"%.0f",h[i])};for(String value:values){TextView text=txt(value,13,DARK,false);text.setGravity(Gravity.CENTER);line.addView(text,new LinearLayout.LayoutParams(0,-2,1));}table.addView(line);if(i<t.length-1)table.addView(rule(),new LinearLayout.LayoutParams(-1,dp(1)));}return table;
    }
    View averageCard(boolean temperature){LinearLayout c=card();c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);c.setMinimumHeight(dp(106));c.setPadding(dp(10),dp(13),dp(10),dp(13));c.setBackground(round(temperature?0xFFFFEAE9:PALE_GREEN,17));c.addView(image(temperature?R.drawable.ic_temperature:R.drawable.ic_drop,temperature?RED:GREEN),new LinearLayout.LayoutParams(dp(25),dp(37)));LinearLayout labels=col();labels.setPadding(dp(7),0,0,0);labels.addView(txt(temperature?"Temp. promedio":"Humedad\npromedio",12,DARK,true));labels.addView(txt(data==null?"--":data.average(temperature),25,DARK,true),margin(-1,7,0,0));c.addView(labels,new LinearLayout.LayoutParams(0,-2,1));return c;}
    View alertsControl(){LinearLayout c=card();c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(11),dp(12),dp(11),dp(12));c.addView(image(R.drawable.ic_notifications,RED),new LinearLayout.LayoutParams(dp(28),dp(34)));LinearLayout labels=col();labels.setPadding(dp(9),0,dp(7),0);labels.addView(txt("Activar alertas",15,DARK,true));labels.addView(txt("Recibe notificaciones cuando los valores salgan del rango establecido",10,0xFF3D487D,false),margin(-1,2,0,0));c.addView(labels,new LinearLayout.LayoutParams(0,-2,1));c.addView(makeSwitch(all,value->{all=value;save();}));return c;}
    void settings(){
        LinearLayout cycle=card();cycle.setPadding(dp(12),dp(12),dp(12),dp(12));cycle.addView(cardTitle(R.drawable.ic_calendar,"Proceso de incubación"));cycle.addView(txt("Fecha de inicio",12,DARK,false),margin(-1,7,0,0));LinearLayout date=row();date.setPadding(dp(10),dp(7),dp(10),dp(7));date.setGravity(Gravity.CENTER_VERTICAL);date.setBackground(round(WHITE,10,0xFFDDE0D9,dp(1)));date.addView(image(R.drawable.ic_calendar,DARK),new LinearLayout.LayoutParams(dp(19),dp(22)));TextView dateText=txt(new SimpleDateFormat("dd/MM/yyyy",Locale.US).format(new Date(startDate)),14,DARK,false);dateText.setPadding(dp(8),0,0,0);date.addView(dateText,new LinearLayout.LayoutParams(0,-2,1));date.addView(txt("⌄",19,DARK,false));date.setOnClickListener(v->pickDate());cycle.addView(date,margin(-1,5,0,0));LinearLayout dayInfo=row();dayInfo.setGravity(Gravity.CENTER_VERTICAL);dayInfo.setPadding(dp(8),dp(5),dp(8),dp(5));dayInfo.setBackground(round(PEACH,10));dayInfo.addView(image(R.drawable.ic_info,TEAL),new LinearLayout.LayoutParams(dp(18),dp(18)));TextView current=txt("Día actual: "+day()+" de 21",12,DARK,true);current.setPadding(dp(7),0,0,0);dayInfo.addView(current);cycle.addView(dayInfo,margin(-1,7,0,0));add(cycle);
        LinearLayout parameters=card();parameters.setPadding(dp(10),dp(12),dp(10),dp(10));LinearLayout heading=row();heading.setGravity(Gravity.TOP);heading.addView(image(R.drawable.ic_target,TEAL),new LinearLayout.LayoutParams(dp(32),dp(34)));LinearLayout labels=col();labels.setPadding(dp(8),0,0,0);labels.addView(txt("Parámetros por etapa",16,DARK,true));labels.addView(txt("Valores de referencia para cada etapa de la incubación",11,0xFF33447A,false),margin(-1,2,0,0));heading.addView(labels,new LinearLayout.LayoutParams(0,-2,1));parameters.addView(heading);parameters.addView(parameterTable(true),margin(-1,8,0,0));add(parameters);
        LinearLayout notifications=card();notifications.setPadding(dp(12),dp(12),dp(12),dp(12));notifications.addView(cardTitle(R.drawable.ic_gear,"Notificaciones",GRAY));notifications.addView(notificationRow(R.drawable.ic_moon,"Notificación en la app",0xFF8554FF,0xFFF0E7FF,appNote,value->appNote=value),margin(-1,5,0,0));notifications.addView(notificationRow(R.drawable.ic_notifications,"Alerta sonora",0xFF2784FF,0xFFE7F2FF,sound,value->sound=value));add(notifications);
        LinearLayout save=row();save.setGravity(Gravity.CENTER);save.setPadding(dp(10),dp(14),dp(10),dp(14));save.setBackground(round(TEAL,15));save.addView(image(R.drawable.ic_save,WHITE),new LinearLayout.LayoutParams(dp(21),dp(23)));TextView text=txt("Guardar configuración",14,WHITE,true);text.setPadding(dp(9),0,0,0);save.addView(text);save.setContentDescription("Guardar configuración");save.setOnClickListener(v->{if(data!=null)data.saveConfiguration();else{save();toast("Configuración guardada.");}});add(save);
    }
    View notificationRow(int icon,String title,int color,int background,boolean checked,Consumer<Boolean> change){LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);ImageView image=image(icon,color);image.setPadding(dp(3),dp(3),dp(3),dp(3));image.setBackground(round(background,20));r.addView(image,new LinearLayout.LayoutParams(dp(25),dp(25)));TextView label=txt(title,13,DARK,false);label.setPadding(dp(8),0,dp(6),0);r.addView(label,new LinearLayout.LayoutParams(0,-2,1));Switch toggle=makeSwitch(checked,change);toggle.setContentDescription(title);r.addView(toggle);return r;}
    View cardTitle(int icon,String title){return cardTitle(icon,title,TEAL);}
    View cardTitle(int icon,String title,int color){LinearLayout row=row();row.setGravity(Gravity.CENTER_VERTICAL);row.addView(image(icon,color),new LinearLayout.LayoutParams(dp(26),dp(29)));TextView name=txt(title,15,DARK,true);name.setPadding(dp(9),0,0,0);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));return row;}
    View rangeLine(boolean temperature){LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(dp(9),dp(8),dp(7),dp(8));line.setBackground(round(WHITE,11,0xFFE1E3DE,dp(1)));line.addView(image(temperature?R.drawable.ic_temperature:R.drawable.ic_drop,temperature?RED:GREEN),new LinearLayout.LayoutParams(dp(22),dp(27)));TextView label=txt((temperature?"Temperatura: ":"Humedad: ")+(cycle()||dateDirty?temperature?band(tMin,tMax," °C"):band(hMin,hMax," %"):"--"),13,DARK,false);label.setPadding(dp(7),0,0,0);line.addView(label,new LinearLayout.LayoutParams(0,-2,1));return line;}
    void pickDate(){Calendar chosen=Calendar.getInstance();chosen.setTimeInMillis(startDate);DatePickerDialog dialog=new DatePickerDialog(this,(view,y,m,d)->{Calendar result=Calendar.getInstance();result.set(y,m,d,12,0,0);startDate=result.getTimeInMillis();dateDirty=true;render();},chosen.get(Calendar.YEAR),chosen.get(Calendar.MONTH),chosen.get(Calendar.DAY_OF_MONTH));dialog.getDatePicker().setMaxDate(System.currentTimeMillis());dialog.show();}
    View rule(){View line=new View(this);line.setBackgroundColor(0xFFEDEFEA);return line;}

    View field(String label,EditText input) { LinearLayout c=col(); c.addView(txt(label,13,GRAY,false)); c.addView(input,margin(-1,4,0,0)); return c; }
    EditText input(float value) { EditText e=new EditText(this); e.setSingleLine(true); e.setInputType(8194|4096|8192); e.setImeOptions(EditorInfo.IME_ACTION_DONE); e.setText(String.format(Locale.US,"%.0f",value)); e.setTextSize(16); e.setTextColor(DARK); e.setPadding(dp(10),0,dp(10),0); e.setBackground(round(WHITE,10,0xFFD8DCE3,dp(1))); return e; }
    float read(EditText e){return Float.parseFloat(e.getText().toString().trim());}

    void alerts() {
        LinearLayout master=card(); master.addView(toggle("Activar alertas","Control general",all,v->{all=v;save();})); add(master);
        add(alertCard("Sin conexión a internet","Detecta la conexión de este teléfono",aInternet,0xFF4176C5,v->{aInternet=v;save();}));
        add(alertCard("Temperatura baja","Cuando baje del mínimo configurado",aTemp,RED,v->{aTemp=v;save();}));
        add(alertCard("Humedad baja","Cuando baje del mínimo configurado",aHum,BLUE,v->{aHum=v;save();}));
        add(alertCard("Corte de energía","El controlador debe reportar la pérdida de alimentación",aPower,0xFFDA9A25,v->{aPower=v;save();}));
        LinearLayout demo=card(); demo.addView(txt("Probar alertas (modo demostración)",16,DARK,true));
        demo.addView(button("Probar temperatura baja",()->updateSensorData(tMin-1,hum,power)));
        demo.addView(button("Probar humedad baja",()->updateSensorData(temp,hMin-1,power)),margin(-1,7,0,0));
        demo.addView(button("Probar corte de energía",()->updateSensorData(temp,hum,false)),margin(-1,7,0,0));
        demo.addView(button("Restaurar valores de demostración",()->updateSensorData(targetTemp(),targetHum(),true)),margin(-1,7,0,0)); add(demo);
        LinearLayout log=card(); log.addView(txt("Eventos recientes",16,DARK,true));
        List<String> recent=data==null?events:data.events(events);
        if(recent.isEmpty())log.addView(txt("Todavía no hay alertas registradas.",13,GRAY,false));
        else for(int i=0;i<Math.min(recent.size(),4);i++)log.addView(txt("•  "+recent.get(i),14,DARK,true)); add(log);
    }
    View alertCard(String title,String detail,boolean checked,int color,Consumer<Boolean> listener) {
        LinearLayout c=card(); c.addView(txt(title,15,DARK,true)); c.addView(txt(detail,12,GRAY,false)); c.addView(toggle("Alerta activa","",checked,listener)); return c;
    }
    Switch makeSwitch(boolean checked,Consumer<Boolean> listener){Switch sw=new Switch(this);GradientDrawable track=round(TEAL,18);track.setSize(dp(45),dp(27));sw.setTrackDrawable(track);GradientDrawable thumb=round(WHITE,30);thumb.setSize(dp(23),dp(23));thumb.setStroke(dp(1),0xFFE3E7DF);sw.setThumbDrawable(thumb);sw.setSplitTrack(false);sw.setSwitchMinWidth(dp(45));sw.setPadding(dp(5),dp(5),0,dp(5));sw.setThumbTintList(android.content.res.ColorStateList.valueOf(WHITE));sw.setTrackTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{TEAL,0xFFCCD1C8}));sw.setChecked(checked);sw.setOnCheckedChangeListener((button,value)->listener.accept(value));return sw;}
    LinearLayout toggle(String title,String detail,boolean checked,Consumer<Boolean> listener){LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);LinearLayout labels=col();labels.addView(txt(title,15,DARK,true));if(!detail.isEmpty())labels.addView(txt(detail,12,GRAY,false));r.addView(labels,new LinearLayout.LayoutParams(0,-2,1));Switch sw=makeSwitch(checked,listener);sw.setContentDescription(title);r.addView(sw);return r;}

    /** Integrar el sensor/controlador llamando este método con cada lectura nueva. */
    public void updateSensorData(float newTemp,float newHum,boolean powerAvailable) {
        if(data!=null)data.demo=true;
        boolean lowT=temp<tMin,lowH=hum<hMin,off=!power; temp=newTemp;hum=newHum;power=powerAvailable;
        if(!lowT&&temp<tMin)raise(Kind.TEMP,"Temperatura baja",temp+" °C",RED);
        if(!lowH&&hum<hMin)raise(Kind.HUMIDITY,"Humedad baja",hum+" %",BLUE);
        if(!off&&!power)raise(Kind.POWER,"Corte de energía","El controlador perdió alimentación.",0xFFDA9A25);
        render();
    }
    @Override protected void onResume(){super.onResume();if(data!=null)data.start();}
    @Override protected void onStop(){if(data!=null)data.stop();super.onStop();}
    @Override protected void onDestroy(){if(data!=null)data.close();super.onDestroy();}
    void openAlerts(){previous=screen;currentScroll=null;screen=Screen.ALERTS;render();}
    void back(){show(screen==Screen.ALERTS?previous:Screen.HOME);}
    @Override public void onBackPressed(){if(screen!=Screen.HOME)back();else super.onBackPressed();}
    void raise(Kind kind,String title,String detail,int color) {
        if(!all||!(kind==Kind.INTERNET?aInternet:kind==Kind.TEMP?aTemp:kind==Kind.HUMIDITY?aHum:aPower))return;
        events.add(0,new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date())+"  "+title);
        if(appNote)toast(title+": "+detail); if(phoneNote)notifyPhone(title,detail);
        if(sound)try{ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_NOTIFICATION,60);tone.startTone(ToneGenerator.TONE_PROP_BEEP,250);handler.postDelayed(tone::release,400);}catch(Exception ignored){}
    }
    void monitorInternet() {
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE); online=hasInternet(cm);
        try {cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback(){
            @Override public void onCapabilitiesChanged(Network n,NetworkCapabilities c){setOnline(c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));}
            @Override public void onLost(Network n){handler.postDelayed(()->setOnline(hasInternet(cm)),300);}
        });}catch(Exception ignored){}
    }
    boolean hasInternet(ConnectivityManager cm){Network n=cm.getActiveNetwork();NetworkCapabilities c=n==null?null:cm.getNetworkCapabilities(n);return c!=null&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);}
    void setOnline(boolean value){handler.post(()->{boolean old=online;online=value;if(old&&!value)raise(Kind.INTERNET,"Sin conexión a internet","El teléfono perdió internet.",0xFF4176C5);render();});}
    void makeChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("env","Alertas ambientales",NotificationManager.IMPORTANCE_HIGH);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    void askPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},9);}
@Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==9&&(results.length==0||results[0]!=PackageManager.PERMISSION_GRANTED)){phoneNote=false;save();toast("Activa el permiso del teléfono para recibir notificaciones.");render();}}
    void notifyPhone(String title,String detail){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;Notification n=new Notification.Builder(this,"env").setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title).setContentText(detail).setAutoCancel(true).build();((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify((int)System.currentTimeMillis(),n);}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    View divider(){View v=new View(this);v.setBackgroundColor(0xFFE9ECF1);return v;}
    TextView button(String label,Runnable action){TextView v=txt(label,14,DARK,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),dp(10),dp(10),dp(10));v.setBackground(round(0xFFFFF0E0,12));v.setOnClickListener(x->action.run());return v;}
    ImageView image(int resource,int color){ImageView i=new ImageView(this);i.setImageResource(resource);i.setColorFilter(color);i.setScaleType(ImageView.ScaleType.FIT_CENTER);return i;}
    LinearLayout card(){LinearLayout c=col();c.setPadding(dp(14),dp(14),dp(14),dp(14));c.setBackground(round(WHITE,18,0xFFF0E9E0,dp(1)));c.setElevation(dp(2));return c;}
    void add(View v){body.addView(v,margin(-1,0,0,0,0,0,0,12));}
    LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    TextView txt(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    GradientDrawable round(int color,int rad){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(rad));return d;}
    GradientDrawable round(int color,int rad,int stroke,int width){GradientDrawable d=round(color,rad);d.setStroke(width,stroke);return d;}
    GradientDrawable gradient(int a,int b,int rad){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});d.setCornerRadius(dp(rad));return d;}
    LinearLayout.LayoutParams weight(float w,int left,int right){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,w);p.setMargins(dp(left),0,dp(right),0);return p;}
    LinearLayout.LayoutParams margin(int width,int top,int left,int right){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,-2);p.setMargins(dp(left),dp(top),dp(right),0);return p;}
    LinearLayout.LayoutParams margin(int width,int top,int left,int right,int el,int et,int er,int eb){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,-2);p.setMargins(dp(left+el),dp(top+et),dp(right+er),dp(eb));return p;}
    int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}

    class CombinedGraph extends View {
        final float[] temperatures,humidities;final String[] labels;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);int selected;
        CombinedGraph(Context c,float[] t,float[] h,String[] x){super(c);temperatures=t;humidities=h;labels=x;selected=t.length/2;setContentDescription("Gráfica de temperatura y humedad. Toca una medición para consultar sus valores.");}
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),dp(228));}
        float position(int i){if(temperatures.length<=1)return .5f;try{String value=labels[i];if(period.equals("Día")){return Math.max(0,Math.min(1,Float.parseFloat(value.split(":")[0])/23f));}String[] date=value.split("/");Calendar today=Calendar.getInstance(TimeZone.getTimeZone("America/Guayaquil"));today.set(Calendar.HOUR_OF_DAY,12);today.set(Calendar.MINUTE,0);today.set(Calendar.SECOND,0);today.set(Calendar.MILLISECOND,0);Calendar point=(Calendar)today.clone();point.set(Calendar.MONTH,Integer.parseInt(date[1])-1);point.set(Calendar.DAY_OF_MONTH,Integer.parseInt(date[0]));if(point.after(today))point.add(Calendar.YEAR,-1);int days=period.equals("Semana")?6:29;Calendar start=(Calendar)today.clone();start.add(Calendar.DAY_OF_YEAR,-days);return Math.max(0,Math.min(1,(point.getTimeInMillis()-start.getTimeInMillis())/(days*86400000f)));}catch(Exception ignored){return i/(temperatures.length-1f);}}
        @Override public boolean onTouchEvent(MotionEvent e){if(temperatures.length==0)return false;if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE){float fraction=(e.getX()-dp(32))/Math.max(1,getWidth()-dp(70));float closest=Float.MAX_VALUE;for(int i=0;i<temperatures.length;i++){float distance=Math.abs(position(i)-fraction);if(distance<closest){closest=distance;selected=i;}}invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_UP)performClick();return true;}
        @Override public boolean performClick(){super.performClick();return true;}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float left=dp(33),right=getWidth()-dp(37),top=dp(28),bottom=getHeight()-dp(31);float lowT=36.5f,highT=39,lowH=40,highH=90;
            for(float v:temperatures){lowT=Math.min(lowT,(float)Math.floor(v*2)/2-.5f);highT=Math.max(highT,(float)Math.ceil(v*2)/2+.5f);}for(float v:humidities){lowH=Math.max(0,Math.min(lowH,(float)Math.floor(v/10)*10-10));highH=Math.min(100,Math.max(highH,(float)Math.ceil(v/10)*10+10));}
            paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(dp(9));paint.setStyle(Paint.Style.FILL);paint.setStrokeWidth(dp(.7f));
            for(int i=0;i<=5;i++){float y=top+(bottom-top)*i/5;paint.setColor(0xFFECEFEA);canvas.drawLine(left,y,right,y,paint);float x=left+(right-left)*i/5;canvas.drawLine(x,top,x,bottom,paint);paint.setColor(DARK);paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(String.format(Locale.US,"%.1f",highT-(highT-lowT)*i/5),left-dp(7),y+dp(3),paint);paint.setTextAlign(Paint.Align.LEFT);canvas.drawText(String.format(Locale.US,"%.0f %%",highH-(highH-lowH)*i/5),right+dp(6),y+dp(3),paint);}
            if(temperatures.length==0){paint.setTextAlign(Paint.Align.CENTER);paint.setColor(GRAY);paint.setTextSize(dp(12));canvas.drawText("Sin mediciones para este periodo",(left+right)/2,(top+bottom)/2,paint);paint.setTextAlign(Paint.Align.LEFT);return;}
            drawSeries(canvas,temperatures,TEAL,lowT,highT,left,right,top,bottom);drawSeries(canvas,humidities,GREEN,lowH,highH,left,right,top,bottom);
            float selectedX=left+(right-left)*position(selected);paint.setColor(0xFFC4CBBF);paint.setStrokeWidth(dp(1));paint.setPathEffect(new DashPathEffect(new float[]{dp(3),dp(3)},0));canvas.drawLine(selectedX,top,selectedX,bottom,paint);paint.setPathEffect(null);
            paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(8));paint.setColor(DARK);int ticks=Math.min(7,labels.length);for(int i=0;i<ticks;i++){int index=ticks==1?0:Math.round(i*(labels.length-1f)/(ticks-1));canvas.drawText(labels[index],left+(right-left)*position(index),bottom+dp(17),paint);}
            String tooltip=String.format(Locale.US,"%.1f °C  ·  %.0f %%",temperatures[selected],humidities[selected]);paint.setTextSize(dp(10));paint.setTypeface(Typeface.DEFAULT_BOLD);float width=paint.measureText(tooltip)+dp(18),bx=Math.max(left,Math.min(right-width,selectedX-width/2));paint.setColor(0xFFFFF2E4);canvas.drawRoundRect(bx,0,bx+width,dp(22),dp(7),dp(7),paint);paint.setColor(DARK);canvas.drawText(tooltip,bx+width/2,dp(15),paint);paint.setTypeface(Typeface.DEFAULT);paint.setTextAlign(Paint.Align.LEFT);
        }
        void drawSeries(Canvas c,float[] values,int color,float min,float max,float left,float right,float top,float bottom){Path path=new Path();for(int i=0;i<values.length;i++){float x=left+(right-left)*position(i),y=bottom-(bottom-top)*(values[i]-min)/(max-min);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}paint.setColor(color);paint.setStrokeWidth(dp(2));paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStyle(Paint.Style.STROKE);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);for(int i=0;i<values.length;i++)c.drawCircle(left+(right-left)*position(i),bottom-(bottom-top)*(values[i]-min)/(max-min),dp(i==selected?4:3),paint);}
    }
    class ProgressMeter extends View {final float value;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);ProgressMeter(Context c,float value){super(c);this.value=value;setContentDescription(Math.round(value*100)+" % completado");}@Override protected void onDraw(Canvas c){float radius=getHeight()/2f;paint.setColor(0xFFDDDDDF);c.drawRoundRect(0,0,getWidth(),getHeight(),radius,radius,paint);if(value>0){paint.setShader(new LinearGradient(0,0,getWidth(),0,0xFF0A7D03,0xFF29AB0D,Shader.TileMode.CLAMP));c.drawRoundRect(0,0,getWidth()*Math.min(1,value),getHeight(),radius,radius,paint);paint.setShader(null);}}}
    class HeaderWave extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);HeaderWave(Context c){super(c);}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();Path path=new Path();path.moveTo(0,h*.82f);path.cubicTo(w*.3f,h*.46f,w*.48f,h*1.13f,w,h*.82f);path.lineTo(w,h);path.lineTo(0,h);path.close();paint.setColor(0x66FFCC62);c.drawPath(path,paint);Path second=new Path();second.moveTo(0,h*.95f);second.cubicTo(w*.4f,h*.67f,w*.57f,h*1.1f,w,h*.94f);second.lineTo(w,h);second.lineTo(0,h);second.close();paint.setColor(0x66FFE1A0);c.drawPath(second,paint);}
    }
}
