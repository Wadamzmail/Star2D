package com.star4droid.star2d.remote;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Fake fingers and keys for the running game. Events go to the game's own InputProcessor (so actors,
 * joysticks and buttons react) and a proxy over Gdx.input answers the polling APIs
 * (isTouched / getX / isKeyPressed...) while a simulation is active. All methods: GL thread.
 */
final class InputSim {
    private InputSim() {}

    private static Input real;
    private static final HashMap<Integer, int[]> DOWN = new HashMap<>();
    private static final HashSet<Integer> KEYS = new HashSet<>();
    private static final HashMap<Integer, Long> KEY_AT = new HashMap<>();
    private static long lastDownAt;

    static synchronized void install() {
        if (real != null) return;
        real = Gdx.input;
        DOWN.clear();
        KEYS.clear();
        KEY_AT.clear();
        InvocationHandler h = (proxy, m, args) -> handle(m, args);
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class[]{Input.class}, h);
    }

    static synchronized void uninstall() {
        if (real == null) return;
        // release everything that is still held
        InputProcessor p = real.getInputProcessor();
        try {
            for (Integer ptr : new HashSet<>(DOWN.keySet())) {
                int[] xy = DOWN.get(ptr);
                if (p != null) p.touchUp(xy[0], xy[1], ptr, 0);
            }
            for (Integer k : new HashSet<>(KEYS)) if (p != null) p.keyUp(k);
        } catch (Throwable ignored) {
        }
        DOWN.clear();
        KEYS.clear();
        Gdx.input = real;
        real = null;
    }

    static boolean active() {
        return real != null;
    }

    private static Object call(Method m, Object[] args) throws Throwable {
        try {
            return m.invoke(real, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static Object handle(Method m, Object[] args) throws Throwable {
        String n = m.getName();
        int a0 = args != null && args.length > 0 && args[0] instanceof Integer ? (Integer) args[0] : -999;
        long now = System.currentTimeMillis();
        synchronized (InputSim.class) {
            switch (n) {
                case "isTouched":
                    if (args == null || args.length == 0) {
                        if (!DOWN.isEmpty()) return true;
                    } else if (DOWN.containsKey(a0)) return true;
                    break;
                case "justTouched":
                    if (now - lastDownAt < 70) return true;
                    break;
                case "getX":
                    if (args == null || args.length == 0) {
                        int[] xy = DOWN.get(0);
                        if (xy != null) return xy[0];
                    } else if (DOWN.containsKey(a0)) return DOWN.get(a0)[0];
                    break;
                case "getY":
                    if (args == null || args.length == 0) {
                        int[] xy = DOWN.get(0);
                        if (xy != null) return xy[1];
                    } else if (DOWN.containsKey(a0)) return DOWN.get(a0)[1];
                    break;
                case "isButtonPressed":
                    if (a0 == 0 && DOWN.containsKey(0)) return true;
                    break;
                case "isButtonJustPressed":
                    if (a0 == 0 && now - lastDownAt < 70) return true;
                    break;
                case "isKeyPressed":
                    if (a0 == Input.Keys.ANY_KEY ? !KEYS.isEmpty() : KEYS.contains(a0)) return true;
                    break;
                case "isKeyJustPressed": {
                    if (a0 == Input.Keys.ANY_KEY) {
                        for (Long t : KEY_AT.values()) if (now - t < 70) return true;
                    } else {
                        Long t = KEY_AT.get(a0);
                        if (t != null && now - t < 70) return true;
                    }
                    break;
                }
                default:
            }
        }
        return call(m, args);
    }

    // ------------------------------------------------------------------ events

    private static InputProcessor processor() {
        InputProcessor p = real != null ? real.getInputProcessor() : Gdx.input.getInputProcessor();
        if (p == null) throw new Gl.ToolError("The game is not taking input right now (is it running?).");
        return p;
    }

    static void down(int x, int y, int pointer) {
        InputProcessor p = processor();
        synchronized (InputSim.class) {
            DOWN.put(pointer, new int[]{x, y});
            lastDownAt = System.currentTimeMillis();
        }
        p.touchDown(x, y, pointer, 0);
    }

    static void move(int x, int y, int pointer) {
        InputProcessor p = processor();
        synchronized (InputSim.class) {
            DOWN.put(pointer, new int[]{x, y});
        }
        p.touchDragged(x, y, pointer);
    }

    static void up(int x, int y, int pointer) {
        InputProcessor p = processor();
        synchronized (InputSim.class) {
            DOWN.remove(pointer);
        }
        p.touchUp(x, y, pointer, 0);
    }

    static void keyDown(int key) {
        InputProcessor p = processor();
        synchronized (InputSim.class) {
            KEYS.add(key);
            KEY_AT.put(key, System.currentTimeMillis());
        }
        p.keyDown(key);
    }

    static void keyUp(int key) {
        InputProcessor p = processor();
        synchronized (InputSim.class) {
            KEYS.remove(key);
        }
        p.keyUp(key);
        p.keyTyped((char) 0);
    }

    /** Key code from a name like LEFT, space, A, 5, ENTER, BACK. */
    static int keyCode(String name) {
        String n = name.trim();
        if (n.isEmpty()) throw new Gl.ToolError("key name is empty");
        int k = Input.Keys.valueOf(n);
        if (k == -1) k = Input.Keys.valueOf(n.substring(0, 1).toUpperCase() + n.substring(1).toLowerCase());
        if (k == -1) k = Input.Keys.valueOf(n.toUpperCase());
        if (k == -1) {
            switch (n.toUpperCase()) {
                case "SPACE": k = Input.Keys.SPACE; break;
                case "ENTER": k = Input.Keys.ENTER; break;
                case "ESC": case "ESCAPE": k = Input.Keys.ESCAPE; break;
                case "BACK": k = Input.Keys.BACK; break;
                case "UP": k = Input.Keys.UP; break;
                case "DOWN": k = Input.Keys.DOWN; break;
                case "LEFT": k = Input.Keys.LEFT; break;
                case "RIGHT": k = Input.Keys.RIGHT; break;
                case "SHIFT": k = Input.Keys.SHIFT_LEFT; break;
                case "CTRL": k = Input.Keys.CONTROL_LEFT; break;
                default:
            }
        }
        if (k == -1) throw new Gl.ToolError("unknown key '" + name + "'. Use names like LEFT, RIGHT, UP, DOWN, SPACE, ENTER, BACK, A..Z, 0..9");
        return k;
    }
}
