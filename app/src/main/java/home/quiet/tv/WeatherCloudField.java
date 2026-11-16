package home.quiet.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** A bounded cloud field. Positions advance in world wind; each slot has its own lifetime. */
final class WeatherCloudField {
    static final int LIMIT=12;
    final float[] positions=new float[LIMIT*4],materials=new float[LIMIT*4],bounds=new float[LIMIT*4];
    final int[] identities=new int[LIMIT];
    private final double[] x=new double[LIMIT],y=new double[LIMIT],z=new double[LIMIT],halfWidth=new double[LIMIT],halfHeight=new double[LIMIT],age=new double[LIMIT],transition=new double[LIMIT];
    private final int[] generation=new int[LIMIT],tile=new int[LIMIT],order=new int[LIMIT];
    private final boolean[] changing=new boolean[LIMIT],replaced=new boolean[LIMIT];
    private final float[][] layers=new float[4][7];
    private final float[] point=new float[3],projected=new float[2];
    private JSONObject profile;
    private int layerCount;private int[] bankGroup;private float[] aspects;
    private boolean ready,rebase;private double lastTime=Double.NaN,lastX,lastZ;
    int visibleCount,recycles;

    void configure(JSONObject next,int[] banks,float[] ratios)throws JSONException{
        if(profile==next||profile!=null&&profile.toString().equals(next.toString()))return;
        JSONArray entries=next.getJSONArray("layers");if(entries.length()<1||entries.length()>4)throw new JSONException("Cloud layer count");
        profile=next;layerCount=entries.length();bankGroup=banks;aspects=ratios;
        for(int l=0;l<layerCount;l++){JSONArray a=entries.getJSONArray(l);if(a.length()!=7)throw new JSONException("Cloud layer layout");for(int j=0;j<7;j++)layers[l][j]=(float)a.getDouble(j);}
        if(ready)for(int i=0;i<LIMIT;i++){changing[i]=true;replaced[i]=false;transition[i]=-i*.12;}
    }
    void reset(){ready=false;lastTime=Double.NaN;}
    void resume(){rebase=true;lastTime=Double.NaN;}
    private static double random(int slot,int cycle,int channel){int h=(slot+1)*0x9e3779b1^cycle*0x85ebca6b^channel*0xc2b2ae35;h=(h^(h>>>16))*0x85ebca6b;h=(h^(h>>>13))*0xc2b2ae35;return ((long)(h^(h>>>16))&0xffffffffL)/4294967296d;}
    private static double smooth(double a,double b,double n){double t=Math.max(0,Math.min(1,(n-a)/(b-a)));return t*t*(3-2*t);}
    private void create(int i,float eye,double dx,double dz,boolean initial){
        int layer=Math.min(layerCount-1,i/3),g=generation[i];float[] spec=layers[layer];double d=spec[0],r=random(i,g,1);
        tile[i]=bankGroup[((i+g)%bankGroup.length+bankGroup.length)%bankGroup.length];
        halfWidth[i]=d*(spec[1]+(spec[2]-spec[1])*random(i,g,2));halfHeight[i]=halfWidth[i]/aspects[tile[i]]*(.96+.08*random(i,g,3));
        x[i]=((i%3)-1)*spec[5]*d+(r-.5)*d*.48;z[i]=d*(.82+.36*random(i,g,4));
        if(!initial){
            if(Math.abs(dx)>Math.abs(dz)*.6&&Math.abs(dx)>1e-9)x[i]=-Math.copySign(d*2.5+halfWidth[i],dx);
            else if(dz<0)z[i]=d*2.8;
        }
        double angle=Math.toRadians(spec[3]+(spec[4]-spec[3])*random(i,g,5));
        y[i]=eye+Math.max(z[i]*Math.tan(angle),halfHeight[i]*1.04+d*.025);
        age[i]=initial?4:0;identities[i]=i+g*LIMIT;
    }
    void sample(double time,double offsetX,double offsetZ,WeatherCamera camera,float[] heading,float eye,float cover){
        double wx=heading[1]*offsetX-heading[0]*offsetZ,wz=heading[0]*offsetX+heading[1]*offsetZ;
        double dt=Double.isNaN(lastTime)?0:Math.max(0,Math.min(.1,time-lastTime));
        if(!ready){
            for(int i=0;i<LIMIT;i++){generation[i]=(int)Math.floor((wx+wz)/(layers[Math.min(layerCount-1,i/3)][0]*4));create(i,eye,0,0,true);float[] band=layers[Math.min(layerCount-1,i/3)];double span=band[0]*band[5]*2,phase=wx-Math.floor(wx/span+.5)*span;x[i]-=phase;changing[i]=false;}
            ready=true;lastX=wx;lastZ=wz;
        }
        if(rebase){lastX=wx;lastZ=wz;rebase=false;}
        double dx=-(wx-lastX),dz=-(wz-lastZ);lastX=wx;lastZ=wz;lastTime=time;
        for(int i=0;i<LIMIT;i++){
            x[i]+=dx;z[i]+=dz;age[i]+=dt;
            if(changing[i]){transition[i]+=dt;if(transition[i]>=.7&&!replaced[i]){generation[i]++;create(i,eye,0,0,true);replaced[i]=true;}if(transition[i]>=1.4)changing[i]=false;}
            double d=layers[Math.min(layerCount-1,i/3)][0],guard=d*2.5+halfWidth[i];
            boolean outgoing=x[i]>guard&&dx>0||x[i]<-guard&&dx<0||z[i]<d*.10||z[i]>d*3.6;
            if(outgoing&&!changing[i]){generation[i]++;create(i,eye,dx,dz,false);recycles++;}
            order[i]=i;
        }
        // Stable front-to-back ordering: identity order breaks ties without per-frame allocation.
        for(int i=1;i<LIMIT;i++){int n=order[i],j=i-1;while(j>=0&&z[order[j]]>z[n]){order[j+1]=order[j];j--;}order[j+1]=n;}
        visibleCount=0;
        for(int k=0;k<LIMIT;k++){
            int i=order[k],o=k*4,l=Math.min(layerCount-1,i/3);double d=layers[l][0];
            double alpha=i<layerCount*3?smooth(random(i,generation[i],6)-.12,random(i,generation[i],6)+.12,Math.min(1,cover*layers[l][6])):0;
            alpha*=smooth(0,.03,cover)*smooth(0,3,age[i])*(1-smooth(d*2.2,d*3.6,z[i]));
            if(changing[i])alpha*=transition[i]<.7?1-smooth(0,.7,transition[i]):smooth(.7,1.4,transition[i]);
            positions[o]=(float)x[i];positions[o+1]=(float)y[i];positions[o+2]=(float)z[i];positions[o+3]=(float)halfWidth[i];
            materials[o]=tile[i];materials[o+1]=(float)halfHeight[i];materials[o+2]=random(i,generation[i],7)>.5?-1:1;materials[o+3]=(float)alpha;
            float minX=10,minY=10,maxX=-10,maxY=-10;
            for(int corner=0;corner<4;corner++){
                double xx=x[i]+((corner&1)==0?-halfWidth[i]:halfWidth[i]);
                point[0]=(float)(heading[1]*xx+heading[0]*z[i]);point[1]=(float)(y[i]-eye+((corner&2)==0?-halfHeight[i]:halfHeight[i]));point[2]=(float)(-heading[0]*xx+heading[1]*z[i]);camera.project(point,projected);
                float px=.5f+.5f*projected[0],py=.5f+.5f*projected[1];minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);
            }
            bounds[o]=minX-.002f;bounds[o+1]=minY-.002f;bounds[o+2]=maxX+.002f;bounds[o+3]=maxY+.002f;
            if(maxX<0||minX>1||maxY<0||minY>1)materials[o+3]=0;
            else if(alpha>.001)visibleCount++;
        }
    }
}
