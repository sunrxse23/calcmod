package de.calcmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class CalcMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Wird aufgerufen, bevor eine Chatnachricht gesendet wird.
        // return false = Nachricht wird NICHT an den Server gesendet.
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String m = message.trim();
            if (!looksLikeMath(m)) return true;
            try {
                double result = new Parser(m).parse();
                Minecraft mc = Minecraft.getInstance();
                                                if (mc.player != null) {
                    mc.player.sendSystemMessage(
                        Component.literal("[Calc] ").withStyle(ChatFormatting.AQUA)
                            .append(Component.literal(m + " = ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(format(result)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
                }
                return false; // nur für dich sichtbar
            } catch (RuntimeException e) {
                return true; // keine gültige Rechnung -> ganz normal senden
            }
        });
    }

    /** Nur Nachrichten aus Rechen-Zeichen mit mindestens einer Ziffer und einem Operator/einer Funktion. */
    static boolean looksLikeMath(String m) {
        if (!m.matches("[0-9a-zA-Z+\\-*/%^().,\\s]+")) return false;
        if (!m.matches(".*[0-9].*")) return false;
        return m.matches(".*[+\\-*/%^].*") || m.toLowerCase().matches(".*(sqrt|sin|cos|tan|abs|ln|log|floor|ceil|round).*");
    }

    static String format(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return String.valueOf(d);
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
        return new java.math.BigDecimal(d).round(new java.math.MathContext(12))
            .stripTrailingZeros().toPlainString();
    }

    /** Rekursiver Parser: + - * / % ^, Klammern, unäres Minus, Funktionen, Konstanten */
    static class Parser {
        private final String s;
        private int pos = -1, ch;

        Parser(String s) { this.s = s.replace(',', '.'); }

        void next() { ch = (++pos < s.length()) ? s.charAt(pos) : -1; }

        boolean eat(int c) {
            while (ch == ' ') next();
            if (ch == c) { next(); return true; }
            return false;
        }

        double parse() {
            next();
            double x = expr();
            if (pos < s.length()) throw new RuntimeException("Unerwartetes Zeichen: '" + (char) ch + "'");
            return x;
        }

        double expr() {
            double x = term();
            for (;;) {
                if (eat('+')) x += term();
                else if (eat('-')) x -= term();
                else return x;
            }
        }

        double term() {
            double x = factor();
            for (;;) {
                if (eat('*')) x *= factor();
                else if (eat('/')) {
                    double d = factor();
                    if (d == 0) throw new RuntimeException("Division durch 0");
                    x /= d;
                }
                else if (eat('%')) x %= factor();
                else return x;
            }
        }

        double factor() {
            if (eat('+')) return factor();
            if (eat('-')) return -factor();
            double x = atom();
            if (eat('^')) x = Math.pow(x, factor()); // rechtsassoziativ
            return x;
        }

        double atom() {
            double x;
            int start = pos;
            if (eat('(')) {
                x = expr();
                if (!eat(')')) throw new RuntimeException("Fehlende schließende Klammer");
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') next();
                try { x = Double.parseDouble(s.substring(start, pos)); }
                catch (NumberFormatException e) { throw new RuntimeException("Ungültige Zahl"); }
            } else if (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z') {
                while (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z') next();
                String name = s.substring(start, pos).toLowerCase();
                switch (name) {
                    case "pi" -> x = Math.PI;
                    case "e" -> x = Math.E;
                    default -> {
                        double a = atom();
                        x = switch (name) {
                            case "sqrt" -> { if (a < 0) throw new RuntimeException("Wurzel aus negativer Zahl"); yield Math.sqrt(a); }
                            case "sin" -> Math.sin(Math.toRadians(a));
                            case "cos" -> Math.cos(Math.toRadians(a));
                            case "tan" -> Math.tan(Math.toRadians(a));
                            case "abs" -> Math.abs(a);
                            case "ln" -> Math.log(a);
                            case "log" -> Math.log10(a);
                            case "floor" -> Math.floor(a);
                            case "ceil" -> Math.ceil(a);
                            case "round" -> (double) Math.round(a);
                            default -> throw new RuntimeException("Unbekannte Funktion: " + name);
                        };
                    }
                }
            } else {
                throw new RuntimeException(ch == -1 ? "Rechnung unvollständig" : "Unerwartetes Zeichen: '" + (char) ch + "'");
            }
            return x;
        }
    }
}
