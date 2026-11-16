package home.quiet.tv;

import java.io.IOException;
import org.json.*;
import static android.opengl.GLES30.*;

/** V13 pass order, seed, light transport, reconstruction and particles. */
@androidx.annotation.RequiresApi(21)
final class WeatherWallpaperRenderer {
    private final WallpaperGl gl;private final WallpaperGpuTimings timings;private final JSONObject presets;
    private final WallpaperGl.Program accumulate,present,precipitation,transmittance,multiScatter,skyView,moonProgram;
    private WallpaperGl.Program march,volumeMarch,materialMarch;
    private final boolean materialsAllowed;private boolean materialClouds,directionalBanks;
    private int bankCount;private float[] bankUv,bankAspect,bankAxes,bankGains;
    private JSONObject bankGroups;private WeatherCloudField clouds=new WeatherCloudField();
    private final WallpaperGl.Target trans,scatter,sky;
    private int shape,detail,curl,relief,body,cumulus,cloudNegative,moonAlbedo;private final int blue;
    private long cloudTextureBytes,moonCacheHour=Long.MIN_VALUE,moonCacheBuilds,celestialCacheBuilds;
    private WallpaperGl.Target moonCache;private WeatherMoon moon=WeatherMoon.at(System.currentTimeMillis());
    private final WeatherMoonPosition.Track lunar=new WeatherMoonPosition.Track();
    private boolean showMoon;private long lunarMinute=Long.MIN_VALUE;
    private final WeatherWind wind=new WeatherWind();
    private float lastMotionX,lastMotionZ,cachedNight=Float.NaN,cachedDisc;
    private final WeatherCamera view=new WeatherCamera();
    private final float[] cloudHeading=new float[2];private double cloudAzimuth;
    private final float[] camera=view.values,previousCamera=new float[4],previousHeading=new float[2];
    private final float[] projectedSun=new float[2],projectedFlash=new float[2],flashPosition=new float[3],particleTint=new float[3];
    private double cameraPitch,cameraFov,cameraAzimuth;private float moonScale;private int sampledCameraRevision=-1;private boolean cameraSettingsDirty;
    private final float[] sunColor=new float[3],moonColor=new float[3];
    private float[] discColor,baseTint,cachedFog;private float baseDiscSize;
    private final Motion motion=new Motion(74719);
    private final WeatherCloudClock cloudClock=new WeatherCloudClock();
    private final float[] sample=new float[5],translation=new float[3],shear=new float[3],jitter=new float[2],displayTranslation=new float[3],displayShear=new float[3];
    private final float[] lastTranslation=new float[3],lastShear=new float[3],lastJitter=new float[2],flash=new float[4];
    private boolean cloudUpdated;
    private long cloudSamples,cloudMotionFrames,presentations;
    private final java.util.Map<String,float[]> lightColors=new java.util.HashMap<>();
    private int foregroundWidth,foregroundHeight;
    private JSONObject animatedWeather;private String animatedKey="";
    private WallpaperGl.Target[] targets;private int width,height,index,frame,frames;
    private int quality,resizedQuality;private float lastFlash;private boolean mediumReady,celestialDirty=true;private WallpaperGl.Target celestial;
    private double lastTime=Double.NEGATIVE_INFINITY;
    private double weatherStart,lastRenderedTime=Double.NaN;
    private WeatherIllumination illumination;
    private String scene="";private JSONObject weather;private float[] sun;
    WeatherWallpaperRenderer(WallpaperGl gl)throws IOException,JSONException{this(gl,null);}
    WeatherWallpaperRenderer(WallpaperGl gl,WallpaperGpuTimings timings)throws IOException,JSONException{this(gl,timings,true);}
    WeatherWallpaperRenderer(WallpaperGl gl,WallpaperGpuTimings timings,boolean cloudMaterials)throws IOException,JSONException{
        this.gl=gl;this.timings=timings;materialsAllowed=cloudMaterials;presets=new JSONObject(gl.text("weather.json"));JSONObject profile=presets.getJSONObject("_camera");camera(profile.getDouble("pitch"),profile.getDouble("verticalFov"),profile.getDouble("azimuthOffset"),(float)profile.getDouble("moonScale"));cloudAzimuth=cameraAzimuth;moonProgram=gl.new Program("vs.glsl","moon-cache.glsl");moonProgram.use();moonProgram.i("albedo",0);accumulate=gl.new Program("vs.glsl","accumulate.glsl");present=gl.new Program("vs.glsl","present.glsl");precipitation=gl.new Program("precip_vs.glsl","precip_fs.glsl");transmittance=gl.new Program("atmosphere-vertex.glsl","transmittance_fs.glsl");multiScatter=gl.new Program("atmosphere-vertex.glsl","multiscatter_fs.glsl");skyView=gl.new Program("atmosphere-vertex.glsl","skyview_fs.glsl");
        trans=gl.new Target(256,64,1,true);scatter=gl.new Target(32,32,1,true);sky=gl.new Target(256,128,1,true);blue=gl.bitmap("blue.png",false);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_REPEAT);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_REPEAT);
        accumulate.use();accumulate.f("camera",camera);accumulate.i("currentColor",2);accumulate.i("currentDepth",3);accumulate.i("previousColor",4);accumulate.i("previousDepth",5);
        present.use();present.f("camera",camera);present.i("image",2);present.i("cloudDepth",4);present.i("celestialImage",3);present.i("moonImage",5);present.i("cloudArt",6);present.i("cloudNegative",8);present.i("skyView",7);present.i("physicalAtmosphere",1);gl.check();
    }
    private void cloudResources(String key)throws IOException,JSONException{
        boolean material=materialsAllowed&&(key.equals("sunny")||key.equals("cloudy")||key.equals("overcast")||key.equals("rain")||key.equals("night")||key.equals("nightRain"));
        if(march!=null&&material==materialClouds)return;
        if(timings!=null)timings.event("cloud-resources "+key);
        for(int texture:new int[]{shape,detail,curl,relief,body})if(texture!=0)gl.removeTexture(texture);
        shape=detail=curl=relief=body=0;
        if(material){
            if(materialMarch==null)materialMarch=gl.new Program("vs.glsl","layered-clouds.glsl");march=materialMarch;
            JSONObject layout=new JSONObject(gl.text("impostors.json"));String encoding=layout.getString("encoding");directionalBanks=encoding.equals("six-way-weighted");if(!directionalBanks&&!encoding.equals("relief-weighted"))throw new IOException("Unsupported cloud material encoding");
            bankGroups=layout.getJSONObject("bankGroups");JSONArray size=layout.getJSONArray("array");int w=size.getInt(0),h=size.getInt(1);bankCount=size.getInt(2);if(bankCount<3||bankCount>8)throw new IOException("Unsupported cloud bank count");
            bankUv=new float[bankCount*4];bankAspect=new float[bankCount];JSONArray rects=layout.getJSONArray("uvRects"),ratios=layout.getJSONArray("aspectRatios");for(int i=0;i<bankCount;i++){bankAspect[i]=(float)ratios.getDouble(i);for(int j=0;j<4;j++)bankUv[i*4+j]=(float)rects.getJSONArray(i).getDouble(j);}
            cumulus=gl.materialArray("cloud-impostors.rgba",w,h,bankCount);cloudNegative=directionalBanks?gl.materialArray(layout.getString("negativeAsset"),w,h,bankCount):0;
            bankAxes=directionalBanks?array(layout,"lightAxes"):new float[]{1,1,1};bankGains=directionalBanks?array(layout,"lightGains"):new float[]{1,1};
            if(!directionalBanks)shape=gl.volume("cloud-blanket.rg8",64,2);cloudTextureBytes=(long)w*h*bankCount*4*4/3*(directionalBanks?2:1)+(directionalBanks?0:64L*64*64*2*8/7);
        }else{
            if(cumulus!=0)gl.removeTexture(cumulus);if(cloudNegative!=0)gl.removeTexture(cloudNegative);cumulus=cloudNegative=0;directionalBanks=false;
            if(volumeMarch==null)volumeMarch=gl.new Program("vs.glsl","march.glsl");march=volumeMarch;
            shape=gl.volume("shape.rgba",128);detail=gl.volume("detail.rgba",64);curl=gl.volume("curl.rgba",64);relief=gl.volume("relief.rgba",128);body=gl.volume("body.rgba",64);cloudTextureBytes=(2L*128*128*128+3L*64*64*64)*4*8/7;
        }
        materialClouds=material;resizedQuality=-1;
        march.use();march.f("camera",camera);march.f("cameraHeading",view.heading);march.f("cloudHeading",cloudHeading);march.i("shapeTex",0);march.i("detailTex",1);march.i("blueNoise",9);march.i("layeredLight",1);march.i("reliefTex",10);march.i("bodyTex",11);march.i("skyView",6);march.i("transmittanceLut",7);march.i("curlTex",8);march.i("physicalAtmosphere",1);march.i("curlEnabled",1);
        march.i("cumulusMaterial",0);march.i("stratusMaterial",1);march.i("cloudVolume",12);
        accumulate.use();accumulate.f("cloudLayer",material?3.4f:1.1f,material?4.8f:3.85f);present.use();present.f("cloudLayer",material?3.4f:1.1f,material?4.8f:3.85f);
    }
    WeatherCloudField cloudState(){return clouds;}
    void cloudState(WeatherCloudField value){if(value!=null){clouds=value;clouds.resume();}}
    void atmosphere(WeatherAtmosphere value){wind.target(value);}
    void moon(WeatherMoon value){moon=value;}
    void camera(double pitch,double fov,double azimuth,float scale){cameraPitch=pitch;cameraFov=fov;cameraAzimuth=azimuth;moonScale=scale;cameraSettingsDirty=true;}
    private void configureCamera(int w,int h){
        int revision=view.revision;boolean jump=view.configure(cameraPitch,cameraFov,(illumination!=null&&illumination.latitude<0?0:180)+cameraAzimuth,1.7f,w,h);
        if(view.revision==revision&&!cameraSettingsDirty)return;
        if(jump)frames=0;double bank=Math.toRadians((illumination!=null&&illumination.latitude<0?0:180)+cloudAzimuth);cloudHeading[0]=(float)Math.sin(bank);cloudHeading[1]=(float)Math.cos(bank);celestialDirty=true;lunarMinute=Long.MIN_VALUE;cameraSettingsDirty=false;
        for(WallpaperGl.Program p:new WallpaperGl.Program[]{march,accumulate,present})if(p!=null){p.use();p.f("camera",camera);p.f("cameraHeading",view.heading);}
        march.use();march.f("cloudHeading",cloudHeading);present.use();present.f("moonScale",moonScale);present.f("detailHeading",cloudHeading);
    }
    private void lunarCache()throws IOException{
        showMoon=illumination!=null&&illumination.lunarPosition;
        if(!showMoon)return;
        long utc=illumination.utc+(illumination.live?(System.nanoTime()-illumination.sampledNanos)/1000000:0);
        lunar.sample(utc,illumination.latitude,illumination.longitude,view);showMoon=lunar.visible;
        moon=WeatherMoon.at(utc);
        if(!showMoon)return;
        if(moonCache==null){moonAlbedo=gl.material("moon-albedo.rgba",256,false);moonCache=gl.new Target(128,128,1,true);}
        long minute=utc/60000;if(lunarMinute==minute&&moonCacheHour==moon.hour)return;
        if(timings!=null)timings.stage("moon-cache");moonCache.bind();moonProgram.use();gl.bind(0,GL_TEXTURE_2D,moonAlbedo);moonProgram.f("phaseLight",lunar.light);moonProgram.f("illuminated",(float)moon.illuminated);gl.draw();moonCacheHour=moon.hour;lunarMinute=minute;moonCacheBuilds++;
    }
    private float[] array(JSONObject o,String name,float... fallback){JSONArray data=o.optJSONArray(name);if(data==null)return fallback;float[] out=new float[data.length()];for(int i=0;i<out.length;i++)out[i]=(float)data.optDouble(i);return out;}
    private float f(String name,float fallback){return (float)weather.optDouble(name,fallback);}
    private float[] a(String name,float... fallback){return array(weather,name,fallback);}
    private void medium(WallpaperGl.Program p){p.f("uRayleigh",.005802f,.013558f,.0331f);p.f("uMie",.01f,.01f/.9f);p.f("uMieH",1.2f);p.f("uOzone",.00065f,.001881f,.000085f);p.f("uMieG",.85f);}
    void restart(){cloudClock.reset();scene="";weatherStart=0;lastRenderedTime=Double.NaN;wind.reset();clouds.resume();lastMotionX=lastMotionZ=0;frames=0;lastTime=Double.NEGATIVE_INFINITY;}
    void reduceLoad(){quality=Math.min(4,quality+1);}
    boolean updatedCloud(){return cloudUpdated;}
    void completed(int level,long stamp,long elapsed){if(timings!=null)timings.completed(scene,level,stamp,elapsed,cloudUpdated,cloudSamples,moonCacheBuilds,celestialCacheBuilds,view.revision);}
    void quality(int value){quality=Math.max(0,Math.min(4,value));}
    void illumination(WeatherIllumination value)throws JSONException{
        if(value!=null)moon=value.moon;
        WeatherIllumination previous=illumination;illumination=value;
        if(previous==null||value==null||previous.latitude!=value.latitude||previous.longitude!=value.longitude)lunarMinute=Long.MIN_VALUE;
        if(previous==value)return;
        if(previous!=null&&value!=null&&Math.abs(previous.night-value.night)<.002&&Math.abs(previous.warm-value.warm)<.002&&Math.abs(previous.sun[0]-value.sun[0])<.001&&Math.abs(previous.sun[1]-value.sun[1])<.001&&Math.abs(previous.sun[2]-value.sun[2])<.001)return;
        boolean jump=previous==null||value==null||Math.abs(previous.night-value.night)>.2;
        illumination=value;lightColors.clear();
        if(!scene.isEmpty()){sun=sceneSun();updateSky();if(jump)frames=0;}
    }
    private float night(){return illumination==null?f("night",0):illumination.night;}
    private float[] light(String name,float... fallback){
        boolean animated=weather==animatedWeather;float[] result=animated?null:lightColors.get(name);if(result!=null)return result;result=a(name,fallback);if(!animated)lightColors.put(name,result);if(illumination==null)return result;
        float warm=illumination.warm*(scene.equals("sunny")||scene.equals("cirrus")?.85f:.25f),night=illumination.night;
        JSONObject twilight=weather.optJSONObject(illumination.rising?"dawnLight":"duskLight"),nightPalette=weather.optJSONObject("nightLight");float[] dusk=array(twilight==null?presets.optJSONObject("sunset"):twilight,name,result),dark=array(nightPalette==null?presets.optJSONObject("night"):nightPalette,name,result);
        for(int i=0;i<result.length;i++)result[i]=(result[i]+(dusk[i]-result[i])*warm)*(1-night)+dark[i]*night*(name.equals("direct")?.08f+.92f*illumination.moonLight:1);
        return result;
    }
    private float cloudDensity(){double value=f("density",1);if(!materialClouds)value*=1-wind.cloudWeight+wind.cloudWeight*smooth(0,.1,wind.cover);if(materialClouds&&weather.optInt("precipitation")==1)value*=.85+.30*wind.rainAmount(f("amount",1));return (float)value;}
    private float[] disc(){discColor[3]=illumination!=null&&illumination.altitude<-.8?0:baseDiscSize;return discColor;}
    private float[] sceneSun(){if(illumination!=null)return illumination.sun;float[] value=a("sun");double yaw=Math.toRadians(180+cloudAzimuth);float x=value[0],z=value[2];value[0]=(float)(Math.cos(yaw)*x+Math.sin(yaw)*z);value[2]=(float)(-Math.sin(yaw)*x+Math.cos(yaw)*z);return value;}
    void scene(String key)throws IOException,JSONException{
        if(scene.equals(key))return;if(timings!=null)timings.event("scene "+key);cloudResources(key);celestialDirty=true;cloudClock.reset();lightColors.clear();scene=key;weatherStart=Double.isNaN(lastRenderedTime)?0:lastRenderedTime;weather=presets.getJSONObject(key);if(materialClouds){String family=f("cloudStructure",0)>1.5?"wet":f("cloudStructure",0)>.5?"blanket":"broken";JSONArray ids=bankGroups.getJSONArray(family);int[] group=new int[ids.length()];for(int i=0;i<group.length;i++)group[i]=ids.getInt(i);clouds.configure(presets.getJSONObject("_cloudLayouts").getJSONObject(key.equals("sunny")?"sunny":family),group,bankAspect);}discColor=a("sunDisc",0,0,0,0);baseDiscSize=discColor[3];baseTint=a("particleTint",1,1,1);JSONObject fog=weather.optJSONObject("fog");cachedFog=fog==null?new float[3]:array(fog,"color");sun=sceneSun();float[] layer=materialClouds?a("cloudLayer",3.4f,4.8f):new float[]{1.1f,3.85f};for(WallpaperGl.Program p:new WallpaperGl.Program[]{march,accumulate,present}){p.use();p.f("cloudLayer",layer);}updateSky();frames=0;lastTime=Double.NEGATIVE_INFINITY;
    }
    private void updateSky(){
        celestialDirty|=cachedNight!=night()||disc()[3]>0||cachedDisc>0;float length=(float)Math.sqrt(sun[0]*sun[0]+sun[1]*sun[1]+sun[2]*sun[2]);float[] normalized={sun[0]/length,sun[1]/length,sun[2]/length};glDisable(GL_BLEND);
        if(!mediumReady){trans.bind();transmittance.use();medium(transmittance);glDrawArrays(GL_TRIANGLES,0,3);
        scatter.bind();multiScatter.use();medium(multiScatter);gl.bind(0,GL_TEXTURE_2D,trans.textures[0]);multiScatter.i("uTransmittance",0);multiScatter.f("uGroundAlbedo",.06f);glDrawArrays(GL_TRIANGLES,0,3);mediumReady=true;}
        sky.bind();skyView.use();medium(skyView);gl.bind(0,GL_TEXTURE_2D,trans.textures[0]);skyView.i("uTransmittance",0);gl.bind(1,GL_TEXTURE_2D,scatter.textures[0]);skyView.i("uMultiScatter",1);skyView.f("uSun",normalized);skyView.f("uCamH",.05f);glDrawArrays(GL_TRIANGLES,0,3);detailSettings();gl.check();
    }
    private void detailSettings(){
        present.use();present.i("cloudDetailEnabled",materialClouds?1:0);present.f("solarDirection",illumination==null?sun:illumination.solar);present.f("sharedSun",sun);present.f("nightAmount",night());
        if(!materialClouds)return;present.i("sixWayBanks",directionalBanks?1:0);present.i("bankCount",bankCount);glUniform4fv(present.u("bankUv"),bankCount,bankUv,0);glUniform1fv(present.u("bankAspect"),bankCount,bankAspect,0);present.f("bankAxes",bankAxes);present.f("bankGains",bankGains);present.f("detailHeading",cloudHeading);present.f("detailZenith",light("zenith"));present.f("detailHorizon",light("horizon"));present.f("detailDirect",light("direct"));present.f("detailAmbient",light("ambient"));present.f("detailHaze",f("haze",1));present.f("detailSunGlow",f("sunGlow",0));present.f("detailStructure",f("cloudStructure",0));present.f("detailSkyMix",f("skyMix",0)*(1-night())+night());
        float[] daylight=a("direct"),dusk=weather.has("dawnLight")?array(weather.optJSONObject("dawnLight"),"direct",daylight):daylight,dark=weather.has("nightLight")?array(weather.optJSONObject("nightLight"),"direct",daylight):daylight;float warmth=illumination==null?0:illumination.warm*(scene.equals("sunny")?.85f:.25f);
        float dayGain=illumination==null?1-night():illumination.solarGain,moonGain=illumination==null?night():illumination.lunarGain;
        for(int i=0;i<3;i++){sunColor[i]=(daylight[i]+(dusk[i]-daylight[i])*warmth)*dayGain;moonColor[i]=dark[i]*moonGain;}
        present.f("detailSolar",illumination==null?sun:illumination.solar);present.f("detailLunar",illumination==null?sun:illumination.lunar);present.f("detailSunColor",sunColor);present.f("detailMoonColor",moonColor);
    }
    private static void difference(float[] out,float[] value,float[] previous){for(int i=0;i<3;i++)out[i]=value[i]-previous[i];}

    private void resize(int w,int h){configureCamera(w,h);if(w==width&&h==height&&resizedQuality==quality)return;if(targets!=null)for(WallpaperGl.Target t:targets)t.destroy();width=w;height=h;resizedQuality=quality;double cloudWidth=materialClouds?(quality==4?160d:quality==3?192d:quality==2?256d:quality==1?320d:480d):(quality==4?256d:quality==3?320d:quality==2?384d:quality==1?480d:640d);double scale=Math.min(materialClouds?.5:.67,cloudWidth/w);int rw=(int)Math.round(w*scale/2)*2,rh=(int)Math.round(h*scale/2)*2;targets=directionalBanks?null:new WallpaperGl.Target[]{gl.new Target(rw,rh,2,true,true),gl.new Target(w,h,2,true,true),gl.new Target(w,h,2,true,true)};index=frames=0;cloudClock.rate(quality==4?12:15);cloudClock.reset();march.use();march.f("resolution",rw,rh);march.i("steps",quality==4?32:quality>=2?48:quality==1?64:112);march.i("lightSteps",quality>=3?1:quality>0?2:4);accumulate.use();accumulate.f("resolution",w,h);accumulate.i("edgeReconstruction",1);accumulate.i("compactKernel",quality>=2?1:0);present.use();present.f("cloudSize",w,h);}
    private JSONObject at(String key,double time)throws JSONException{
        JSONObject base=presets.getJSONObject(key);if(!key.equals("afterRain")&&!key.equals("showers")&&!key.equals("blizzard"))return base;if(!animatedKey.equals(key)){animatedWeather=new JSONObject(base.toString());animatedKey=key;}base=animatedWeather;if(key.equals("afterRain")){double clear=smooth(0,100,Math.max(0,time-weatherStart)),amount=1-smooth(.04,.78,clear);base.put("amount",amount).put("count",amount>.001?260:0).put("coverage",.30-.64*clear).put("density",1.22-.40*clear).put("skyMix",.94-.82*clear).put("haze",1.25-.60*clear);blend(base,"zenith",new double[]{.055,.08,.12},new double[]{.018,.13,.36},clear);blend(base,"horizon",new double[]{.18,.25,.32},new double[]{.32,.57,.82},clear);blend(base,"direct",new double[]{.16,.20,.27},new double[]{1.08,1.03,.93},clear);blend(base,"ambient",new double[]{.43,.55,.70},new double[]{1,1.02,1.08},clear);}
        else if(key.equals("showers")){double amount=.06+.94*smooth(.22,.78,Motion.noise(74719^825,time,29));JSONArray band=base.optJSONArray("rainBand");if(band==null)band=new JSONArray();band.put(0,-2.3+4.6*Motion.noise(74719^903,time,43)).put(1,.75);base.put("amount",amount).put("coverage",.01+.29*amount).put("density",.92+.32*amount).put("skyMix",.28+.66*amount).put("haze",.72+.53*amount).put("rainBand",band);blend(base,"direct",new double[]{.9,.93,.96},new double[]{.16,.20,.27},amount);blend(base,"ambient",new double[]{.94,1,1.05},new double[]{.43,.55,.70},amount);}
        else if(key.equals("blizzard"))base.put("windScale",2.9+Motion.noise(74719^662,time,41));return base;
    }
    private static void blend(JSONObject data,String key,double[] from,double[] to,double amount)throws JSONException{JSONArray values=data.optJSONArray(key);if(values==null)values=new JSONArray();for(int i=0;i<from.length;i++)values.put(i,from[i]+(to[i]-from[i])*amount);data.put(key,values);}
    private static double smooth(double a,double b,double x){double t=Math.max(0,Math.min(1,(x-a)/(b-a)));return t*t*(3-2*t);}
    void render(double time,int w,int h,int destination)throws IOException,JSONException{render(time,w,h,w,h,destination);}
    void render(double time,int w,int h,int outputWidth,int outputHeight,int destination)throws IOException,JSONException{
        String key=scene;resize(w,h);glDisable(GL_BLEND);double cloudTime=time+(time-weatherStart)*((float)presets.getJSONObject(key).optDouble("cloudSpeed",1)-1);motion.sample(cloudTime,sample);double blend=-Math.expm1(-Math.max(cloudTime,0)/60);
        double dt=Double.isNaN(lastRenderedTime)?0:Math.max(0,time-lastRenderedTime);
        if(Double.isNaN(lastRenderedTime)){wind.offsetX=sample[0]*1.10;wind.offsetZ=sample[1]*.85;lastMotionX=sample[0];lastMotionZ=sample[1];}
        wind.step(dt,(sample[0]-lastMotionX)*1.10,(sample[1]-lastMotionZ)*.85,-.18*sample[4]/.13);lastMotionX=sample[0];lastMotionZ=sample[1];translation[0]=(float)wind.offsetX;translation[2]=(float)wind.offsetZ;
        shear[0]=(float)(1.8*Math.tanh(sample[0]*.7/1.8)+.24*blend*(Motion.noise(74719^451,cloudTime,91)*2-1));shear[2]=(float)(-.6*Math.tanh(sample[1]*.3/.6)+.10*blend*(Motion.noise(74719^919,cloudTime,127)*2-1));
        difference(displayTranslation,translation,lastTranslation);difference(displayShear,shear,lastShear);
        boolean valid=frames>0&&time-lastTime>=0&&time-lastTime<5;WallpaperGl.Target raw=targets==null?null:targets[0],past=targets==null?null:targets[1+index],next=targets==null?null:targets[1+(1-index)];
        flash[0]=flash[1]=flash[3]=0;flash[2]=1;float radius=1;if(presets.getJSONObject(key).optBoolean("lightning")){int slot=(int)Math.floor(time/16);double start=slot*16+2+6*Motion.random(74719^1701,slot),age=time-start,gain=.85+.30*Motion.random(74719^991,slot);flash[0]=(float)(-2.4+4.8*Motion.random(74719^701,slot));flash[1]=(float)(1.9+1.3*Motion.random(74719^211,slot));flash[2]=(float)(4.8+2.6*Motion.random(74719^517,slot));flash[3]=(float)(age<0||age>1.8?0:gain*(.75*Math.exp(-Math.pow((age-.26)/.18,2))+.20*Math.exp(-Math.pow((age-.82)/.26,2))));radius=(float)(1.6+.7*Motion.random(74719^391,slot));}
        float flashX=flash[0],flashZ=flash[2];flash[0]=view.heading[1]*flashX+view.heading[0]*flashZ;flash[2]=-view.heading[0]*flashX+view.heading[1]*flashZ;
        boolean updateCloud=directionalBanks||cloudClock.sample(time,quality<3||frames==0||view.revision!=sampledCameraRevision||time<=lastTime||Math.abs(flash[3]-lastFlash)>.02f);cloudUpdated=updateCloud;presentations++;
        if(updateCloud){
        weather=at(key,time);cloudSamples++;double magnitude=Math.hypot(shear[0],shear[2])/3.85;float stretch=(float)((Math.sqrt(magnitude*magnitude+4)+magnitude)*.5);jitter[0]=jitter[1]=0;
        if(!directionalBanks){if(timings!=null)timings.stage("march");glDisable(GL_BLEND);raw.bind();march.use();gl.bind(0,materialClouds?GL_TEXTURE_2D_ARRAY:GL_TEXTURE_3D,materialClouds?cumulus:shape);gl.bind(1,materialClouds?GL_TEXTURE_2D_ARRAY:GL_TEXTURE_3D,materialClouds?cloudNegative:detail);march.f("jitter",jitter);march.f("translation",translation);march.f("shear",shear);march.f("deformationStretch",stretch);march.f("evolution",sample[2],0,sample[3]);march.f("origin",motion.origin);march.i("frameIndex",0);march.f("sharedSun",sun);march.f("solarDirection",illumination==null?sun:illumination.solar);march.f("nightAmount",night());march.f("sunGlow",f("sunGlow",0));march.f("cloudStructure",f("cloudStructure",0));
        if(materialClouds)gl.bind(12,GL_TEXTURE_3D,shape);gl.bind(9,GL_TEXTURE_2D,blue);gl.bind(10,GL_TEXTURE_3D,relief);gl.bind(11,GL_TEXTURE_3D,body);
        march.f("cloudParameters",(float)wind.coverage(f("coverage",0)),cloudDensity());march.f("skyOverride",f("skyMix",0)*(1-night())+night());march.f("skyZenith",light("zenith"));march.f("skyHorizon",light("horizon"));march.f("directLight",light("direct"));march.f("ambientLight",light("ambient"));march.f("hazeAmount",f("haze",1));march.i("cloudKind",weather.optInt("cloudKind"));march.f("skyFalloff",f("skyFalloff",0));march.f("sunDisc",disc());march.f("lightning",flash);march.f("lightningRadius",radius);JSONObject fog=weather.optJSONObject("fog");float[] fogColor=cachedFog;float fogGain=illumination==null?1:1-night()*.96f;march.f("fogParameters",fogColor[0]*fogGain,fogColor[1]*fogGain,fogColor[2]*fogGain,fog==null?0:(float)fog.optDouble("density"));
        gl.bind(6,GL_TEXTURE_2D,sky.textures[0]);gl.bind(7,GL_TEXTURE_2D,trans.textures[0]);gl.bind(8,GL_TEXTURE_3D,curl);gl.draw();
        if(timings!=null)timings.stage("accumulate");next.bind();accumulate.use();gl.bind(2,GL_TEXTURE_2D,raw.textures[0]);gl.bind(3,GL_TEXTURE_2D,raw.textures[1]);gl.bind(4,GL_TEXTURE_2D,past.textures[0]);gl.bind(5,GL_TEXTURE_2D,past.textures[1]);accumulate.f("reactive",Math.min(1,Math.abs(flash[3]-lastFlash)*4));accumulate.f("jitter",jitter);accumulate.f("previousJitter",frames==0?jitter:lastJitter);accumulate.f("deltaTranslation",displayTranslation);accumulate.f("deltaShear",displayShear);accumulate.f("previousCamera",frames==0?camera:previousCamera);accumulate.f("previousHeading",frames==0?view.heading:previousHeading);accumulate.i("valid",valid?1:0);gl.draw();}
        }else next=past;
        lunarCache();
        boolean lightVisible=night()>0||disc()[3]>0;int cw=lightVisible?outputWidth:1,ch=lightVisible?outputHeight:1;
        if(celestial==null||celestial.w!=cw||celestial.h!=ch){if(celestial!=null)celestial.destroy();celestial=gl.new Target(cw,ch,1,true);celestialDirty=true;}
        present.use();gl.bind(7,GL_TEXTURE_2D,sky.textures[0]);if(materialClouds){gl.bind(6,GL_TEXTURE_2D_ARRAY,cumulus);gl.bind(8,GL_TEXTURE_2D_ARRAY,cloudNegative==0?cumulus:cloudNegative);present.f("detailParameters",(float)wind.coverage(f("coverage",0)),cloudDensity());present.f("detailTranslation",translation);present.f("detailShear",shear);present.f("detailEvolution",sample[2],0,sample[3]);clouds.sample(time,wind.offsetX,wind.offsetZ,view,cloudHeading,camera[3],Math.max(0,Math.min(1,((float)wind.coverage(f("coverage",0))+.45f)/.75f)));present.i("cloudCount",WeatherCloudField.LIMIT);glUniform4fv(present.u("cloudPosition"),WeatherCloudField.LIMIT,clouds.positions,0);glUniform4fv(present.u("cloudMaterial"),WeatherCloudField.LIMIT,clouds.materials,0);glUniform4fv(present.u("cloudBounds"),WeatherCloudField.LIMIT,clouds.bounds,0);}if(foregroundWidth!=outputWidth||foregroundHeight!=outputHeight){foregroundWidth=outputWidth;foregroundHeight=outputHeight;present.f("viewSize",outputWidth,outputHeight);}
        if(celestialDirty){present.f("solarDirection",illumination==null?sun:illumination.solar);present.f("sharedSun",sun);present.f("sunDisc",disc());present.f("sunSoftness",f("sunSoftness",0));present.f("nightAmount",night());if(timings!=null)timings.stage("celestial-cache");gl.bind(3,GL_TEXTURE_2D,0);celestial.bind();present.i("celestialOnly",1);gl.draw();celestialDirty=false;celestialCacheBuilds++;cachedNight=night();cachedDisc=disc()[3];}
        glBindFramebuffer(GL_FRAMEBUFFER,destination);glViewport(0,0,outputWidth,outputHeight);present.i("celestialOnly",0);boolean warp=!directionalBanks&&!updateCloud&&valid&&time-lastTime<.2;if(warp)cloudMotionFrames++;if(timings!=null)timings.stage(warp?"present-warp":"present");present.i("reprojectCloud",warp?1:0);if(warp){present.f("displayTranslation",displayTranslation);present.f("displayShear",displayShear);gl.bind(4,GL_TEXTURE_2D,next.textures[1]);}gl.bind(2,GL_TEXTURE_2D,next==null?0:next.textures[0]);gl.bind(3,GL_TEXTURE_2D,celestial.textures[0]);present.i("showMoon",showMoon?1:0);if(showMoon){present.f("moonDirection",lunar.direction);present.f("moonAngularRadius",lunar.angularRadius);present.f("moonGain",(.08f+.92f*night())*lunar.visibility);}gl.bind(5,GL_TEXTURE_2D,moonCache==null?0:moonCache.textures[0]);gl.draw();
        int count=weather.optInt("count");float rainAmount=(float)wind.rainAmount(f("amount",1));if(count>0&&rainAmount>.001f){if(timings!=null)timings.stage("precipitation");precipitation.use();precipitation.f("time",(float)time);precipitation.f("wind",(view.right(wind.particleWind,wind.particleNorth)/Math.max(.2f,camera[1]+camera[0]*view.forward(wind.particleWind,wind.particleNorth)))*(weather.optInt("precipitation")==2?.55f:1)*f("windScale",1));precipitation.f("fallScale",f("fallScale",1));precipitation.f("nightAmount",night());precipitation.f("amount",rainAmount);precipitation.f("snowStreak",f("snowStreak",0));precipitation.f("rainBand",a("rainBand",0,0));view.project(sun,projectedSun);precipitation.f("lightCenter",projectedSun);flashPosition[0]=flash[0];flashPosition[1]=flash[1]-camera[3];flashPosition[2]=flash[2];view.project(flashPosition,projectedFlash);precipitation.f("flash",projectedFlash[0],projectedFlash[1],flash[3]);precipitation.i("kind",weather.optInt("precipitation"));precipitation.f("viewSize",outputWidth,outputHeight);float n=illumination==null?0:night();particleTint[0]=baseTint[0]*(1-n*.45f);particleTint[1]=baseTint[1]*(1-n*.25f);particleTint[2]=baseTint[2];precipitation.f("tint",particleTint);glEnable(GL_BLEND);glBlendFunc(GL_ONE,GL_ONE_MINUS_SRC_ALPHA);glDrawArraysInstanced(GL_TRIANGLES,0,6,count);glDisable(GL_BLEND);}
        if(updateCloud){System.arraycopy(camera,0,previousCamera,0,4);System.arraycopy(view.heading,0,previousHeading,0,2);sampledCameraRevision=view.revision;lastFlash=flash[3];index=1-index;frame++;frames=directionalBanks?0:valid?frames+1:1;lastTime=time;System.arraycopy(translation,0,lastTranslation,0,3);System.arraycopy(shear,0,lastShear,0,3);System.arraycopy(jitter,0,lastJitter,0,2);}lastRenderedTime=time;gl.check();
    }
    static final class Motion {
        final int seed;final Wind x,z,mx,mz;final float[] origin;
        Motion(int seed){this.seed=seed;x=new Wind(seed^131,.13,.36,47);z=new Wind(seed^719,.014,1.5,67);mx=new Wind(seed^881,.0035,.65,113);mz=new Wind(seed^997,.0027,.60,137);origin=new float[]{(float)(random(seed,20)*64),(float)(random(seed,21)*64),(float)(random(seed,22)*64)};}
        static double random(int seed,int index){int x=seed^(index*0x9e3779b1);x=(x^(x>>>16))*0x85ebca6b;x=(x^(x>>>13))*0xc2b2ae35;return ((long)(x^(x>>>16))&0xffffffffL)/4294967296d;}
        static double noise(int seed,double t,double interval){int n=(int)Math.floor(t/interval);double u=t/interval-n,s=u*u*(3-2*u),a=random(seed,n);return a+(random(seed,n+1)-a)*s;}
        void sample(double t,float[] out){out[0]=(float)x.distance(t);out[1]=(float)z.distance(t);out[2]=(float)mx.distance(t);out[3]=(float)mz.distance(t);out[4]=(float)x.speed(t);}
        static final class Wind {final java.util.Map<Integer,Double> areas=new java.util.HashMap<>();int maximum;double accumulated;final int seed;final double base,variation,interval;Wind(int seed,double b,double v,double i){this.seed=seed;base=b;variation=v;interval=i;}double target(int n){return base*(1+variation*(random(seed,n)*2-1));}double speed(double t){int n=(int)Math.floor(t/interval);double u=t/interval-n,a=target(n);return a+(target(n+1)-a)*u*u*(3-2*u);}double distance(double t){int n=(int)Math.floor(t/interval);while(maximum<n){accumulated+=(target(maximum)+target(maximum+1))*.5*interval;areas.put(++maximum,accumulated);}double area=n==0?0:areas.get(n);double u=t/interval-n,a=target(n),b=target(n+1);return area+interval*(a*u+(b-a)*(u*u*u-.5*u*u*u*u));}}
    }
}
