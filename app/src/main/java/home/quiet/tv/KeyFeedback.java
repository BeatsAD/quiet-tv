package home.quiet.tv;

import android.content.Context;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.WeakHashMap;

/** Sounds follow user navigation and accepted clicks, never automatic focus restoration. */
final class KeyFeedback {
    interface Dispatch {boolean run();}
    private static final WeakHashMap<View,Navigation> roots=new WeakHashMap<>();
    private static Input input;
    private static final class Input {
        final KeyEvent key;
        boolean action;
        Input(KeyEvent key){this.key=key;}
    }
    private static final class Navigation {
        boolean pending;
    }
    static void attach(View root){
        if(roots.containsKey(root))return;
        Navigation navigation=new Navigation();roots.put(root,navigation);root.setSoundEffectsEnabled(false);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((before,after)->{
            if(after!=null)after.setSoundEffectsEnabled(false);
            if(navigation.pending&&before!=after&&after!=null&&after.hasWindowFocus())KeySounds.play(after.getContext(),KeySoundPolicy.MOVE);
        });
    }
    static boolean dispatch(View root,KeyEvent key,Dispatch next){
        if(input!=null&&input.key==key)return next.run();
        attach(root);View focus=root.findFocus();if(focus!=null)focus.setSoundEffectsEnabled(false);
        Navigation navigation=roots.get(root);
        if(direction(key.getKeyCode())&&key.getAction()==KeyEvent.ACTION_DOWN){
            navigation.pending=true;
            // Native ViewRoot focus navigation can run after Activity.dispatchKeyEvent returns.
            root.post(()->navigation.pending=false);
        }else navigation.pending=false;
        Input previous=input;input=new Input(key);
        try{return next.run();}finally{input=previous;}
    }
    static boolean direction(int key){return key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN;}
    private static boolean confirmKey(int key){return key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER||key==KeyEvent.KEYCODE_BUTTON_A||key==KeyEvent.KEYCODE_BUTTON_SELECT;}
    static void moved(Context context){if(input!=null&&direction(input.key.getKeyCode())&&input.key.getAction()==KeyEvent.ACTION_DOWN)KeySounds.play(context,KeySoundPolicy.MOVE);}
    static void confirm(Context context){
        if(input!=null&&confirmKey(input.key.getKeyCode())&&input.key.getAction()==KeyEvent.ACTION_UP&&!input.key.isCanceled())action(context,KeySoundPolicy.CONFIRM);
    }
    static void back(Context context){if(input!=null&&!direction(input.key.getKeyCode())&&!input.key.isCanceled())action(context,KeySoundPolicy.BACK);}
    static void returned(Context context){if(input==null)KeySounds.play(context,KeySoundPolicy.BACK);else back(context);}
    private static void action(Context context,int sound){if(!input.action){input.action=true;KeySounds.play(context,sound);}}
    static void clicked(View view,Runnable action){action.run();confirm(view.getContext());}
    static class Button extends android.widget.Button {
        Button(Context context){super(context);setSoundEffectsEnabled(false);}
        @Override public boolean performClick(){boolean accepted=super.performClick();if(accepted)confirm(getContext());return accepted;}
    }
    static class Text extends TextView {
        Text(Context context){super(context);setSoundEffectsEnabled(false);}
        @Override public boolean performClick(){boolean accepted=super.performClick();if(accepted)confirm(getContext());return accepted;}
    }
    static final class List extends ListView {
        List(Context context){super(context);setSoundEffectsEnabled(false);}
        @Override public boolean onKeyDown(int key,KeyEvent event){int before=getSelectedItemPosition();boolean handled=super.onKeyDown(key,event);if(before!=getSelectedItemPosition())moved(getContext());return handled;}
        @Override public boolean performItemClick(View view,int position,long id){boolean accepted=super.performItemClick(view,position,id);if(accepted)confirm(getContext());return accepted;}
    }
    static void observeAlert(android.app.AlertDialog dialog){
        if(dialog instanceof Alert)return;
        attach(dialog.getWindow().getDecorView());
        dialog.setOnKeyListener((d,key,event)->dispatch(dialog.getWindow().getDecorView(),event,()->false));
        dialog.setOnCancelListener(d->KeySounds.play(dialog.getContext(),KeySoundPolicy.BACK));
    }
    static class Alert extends android.app.AlertDialog {
        Alert(Context context){super(context);setOnCancelListener(d->KeySounds.play(context,KeySoundPolicy.BACK));}
        @Override protected void onCreate(Bundle state){super.onCreate(state);attach(getWindow().getDecorView());}
        @Override public boolean dispatchKeyEvent(KeyEvent key){return dispatch(getWindow().getDecorView(),key,()->super.dispatchKeyEvent(key));}
    }
    static class Dialog extends android.app.Dialog {
        Dialog(Context context){super(context);setOnCancelListener(d->KeySounds.play(context,KeySoundPolicy.BACK));}
        @Override protected void onCreate(Bundle state){super.onCreate(state);attach(getWindow().getDecorView());}
        @Override public boolean dispatchKeyEvent(KeyEvent key){return dispatch(getWindow().getDecorView(),key,()->super.dispatchKeyEvent(key));}
    }
    static final class Popup extends PopupWindow {
        private final Context context;
        private boolean quiet;
        Popup(Context context,View content,int width,int height){super(content,width,height,true);this.context=context;}
        void closeQuietly(){quiet=true;try{dismiss();}finally{quiet=false;}}
        @Override public void dismiss(){boolean showing=isShowing();super.dismiss();if(showing&&!quiet){if(input==null)KeySounds.play(context,KeySoundPolicy.BACK);else if(input.key.getKeyCode()==KeyEvent.KEYCODE_BACK)back(context);}}
    }
    static class Menu extends LinearLayout {
        Menu(Context context){super(context);}
        @Override public boolean dispatchKeyEvent(KeyEvent key){return dispatch(getRootView(),key,()->super.dispatchKeyEvent(key));}
    }
}
