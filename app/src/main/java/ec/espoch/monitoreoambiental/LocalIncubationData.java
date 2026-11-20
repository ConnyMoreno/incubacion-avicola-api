package ec.espoch.monitoreoambiental;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Datos locales de demostración. No utiliza red, servidor ni base de datos. */
final class LocalIncubationData implements AutoCloseable {
    final MainActivity app;
    final JSONArray stages;
    boolean hasCycle=true,hasReading=true,connected=false,demo=true;
    float tempTolerance=.5f,humTolerance=5;
    private float[] temperatures=new float[0],humidities=new float[0];
    private String[] labels=new String[0];
    private String loadedPeriod="";
    LocalIncubationData(MainActivity app){this.app=app;stages=app.fallbackStages();app.temp=37.7f;app.hum=55;refresh();}
    void start(){refresh();}
    void stop(){}
    void refresh(){if(!app.period.equals(loadedPeriod))changePeriod();}
    void changePeriod(){
        int count=app.period.equals("Día")?24:app.period.equals("Semana")?7:30;
        temperatures=new float[count];humidities=new float[count];labels=new String[count];
        float[] weekT={37.6f,37.65f,37.65f,37.65f,37.61f,37.6f,37.6f};
        float[] weekH={57,57,57.1f,57.2f,55.7f,54.7f,56.2f};
        Calendar date=Calendar.getInstance();date.add(Calendar.DAY_OF_YEAR,-count+1);
        for(int i=0;i<count;i++){
            if(count==7){temperatures[i]=weekT[i];humidities[i]=weekH[i];}
            else{temperatures[i]=37.65f+(float)Math.sin(i*.55)*.12f;humidities[i]=55+(float)Math.sin(i*.4)*2.5f;}
            if(count==24)labels[i]=String.format(Locale.US,"%02d:00",i);
            else{labels[i]=new java.text.SimpleDateFormat("dd/MM",Locale.US).format(date.getTime());date.add(Calendar.DAY_OF_YEAR,1);}
        }
        loadedPeriod=app.period;
    }
    float[] values(boolean temperature){refresh();return temperature?temperatures:humidities;}
    String average(boolean temperature){float[] values=values(temperature);float total=0;for(float value:values)total+=value;return String.format(Locale.US,temperature?"%.1f °C":"%.0f %%",total/values.length);}
    String label(int position){return position>=0&&position<labels.length?labels[position]:"";}
    boolean displayReading(){return true;}
    List<String> events(List<String> local){return new ArrayList<>(local);}
    void saveConfiguration(){hasCycle=true;app.applyStage();app.save();app.dateDirty=false;app.toast("Configuración guardada.");app.render();}
    public void close(){}
}
