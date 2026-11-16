package home.quiet.tv;

import java.util.Locale;

/** Real normalized condition codes use the prototype's scene and hour rules. */
final class WeatherWallpaperScene {
    private WeatherWallpaperScene(){}
    static String name(String key,boolean chinese){
        String[] keys={"sunny","cloudy","overcast","rain","snow","fog","storm","afterRain","cirrus","blizzard","showers","morningFog","dust","night","nightRain","nightSnow","sunrise","sunset"},zh={"晴天","多云","阴天","雨天","雪天","雾天","雷雨","雨后初晴","高空卷云","风雪","阵雨","晨雾","沙尘","夜晚","夜雨","夜雪","日出","日落"},en={"Sunny","Cloudy","Overcast","Rain","Snow","Fog","Thunderstorm","After rain","Cirrus","Blizzard","Showers","Morning fog","Dust","Night","Night rain","Night snow","Sunrise","Sunset"};
        for(int i=0;i<keys.length;i++)if(keys[i].equals(key))return chinese?zh[i]:en[i];return chinese?"多云":"Cloudy";
    }
    static String key(String description,String icon,boolean follow,int hour){
        String text=description==null?"":description.toLowerCase(Locale.ROOT),key="cloudy";
        int code;try{code=Integer.parseInt(icon);}catch(NumberFormatException invalid){code=999;}
        if(text.contains("雨后初晴")||text.contains("after rain")||text.contains("clearing"))key="afterRain";
        else if(text.contains("卷云")||text.contains("cirrus"))key="cirrus";
        else if(text.contains("风雪")||text.contains("暴雪")||text.contains("blizzard"))key="blizzard";
        else if(text.contains("晨雾")||text.contains("morning fog"))key="morningFog";
        else if(text.contains("沙")||text.contains("尘")||text.contains("dust")||text.contains("sand"))key="dust";
        else if(code>=302&&code<=304)key="storm";
        else if(code==300||code==301||text.contains("阵雨")||text.contains("showers"))key="showers";
        else if(code>=300&&code<400)key="rain";
        else if(code>=400&&code<500)key="snow";
        else if(code>=500&&code<600)key="fog";
        else if(code==100||code==150)key="sunny";
        else if(code==104)key="overcast";
        int period=!follow?1:hour>=5&&hour<7?0:hour>=7&&hour<17?1:hour>=17&&hour<19?2:3;
        if(period==3){if(key.equals("rain")||key.equals("showers"))return "nightRain";if(key.equals("snow")||key.equals("blizzard"))return "nightSnow";if(key.equals("sunny")||key.equals("cloudy")||key.equals("overcast")||key.equals("cirrus")||key.equals("afterRain"))return "night";}
        if(period==0&&key.equals("fog"))return "morningFog";
        if((period==0||period==2)&&(key.equals("sunny")||key.equals("cloudy")||key.equals("cirrus")))return period==0?"sunrise":"sunset";
        return key;
    }
}
