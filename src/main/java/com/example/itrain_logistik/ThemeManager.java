package com.example.itrain_logistik;

import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Window;

/**
 * Wendet das gewaehlte Farbschema (hell/dunkel bzw. eine frei gewaehlte
 * Farbkombination) an. Wortgleich uebernommen aus dem Schwesterprojekt
 * iTrain_import_export (dort ausfuehrlich begruendet) - eine zentrale
 * Anmeldung an {@link Window#getWindows()} faerbt jedes Fenster und jeden
 * Dialog ein, statt an jeder der vielen {@code new Alert(...)}-Stellen daran
 * denken zu muessen.
 */
public final class ThemeManager {

    private static final String DARK_STYLESHEET = "dark-theme.css";

    private static final String WATCHED_KEY = "iTrainLogistik.themeWatched";

    /** Vorschau-Farben aus dem geoeffneten {@link CustomColorDialog}. */
    private static String previewBackground;
    private static String previewText;

    private ThemeManager() {
    }

    public static void preview(String backgroundWeb, String textWeb) {
        previewBackground = backgroundWeb;
        previewText = textWeb;
        applyToAllWindows(AppSettings.getInstance().getTheme());
    }

    public static void endPreview() {
        previewBackground = null;
        previewText = null;
        applyToAllWindows(AppSettings.getInstance().getTheme());
    }

    private static String[] effectiveColors() {
        if (previewBackground != null && previewText != null) {
            return new String[]{previewBackground, previewText};
        }
        AppSettings settings = AppSettings.getInstance();
        if (settings.isCustomColorActive()
                && settings.getCustomBackgroundColor() != null && settings.getCustomTextColor() != null) {
            return new String[]{settings.getCustomBackgroundColor(), settings.getCustomTextColor()};
        }
        return AppSettings.THEME_DARK.equals(settings.getTheme())
                ? new String[]{"#2b2b2b", "#e0e0e0"}
                : new String[]{"#ececec", "#000000"};
    }

    public static Color textColor() {
        return Color.web(effectiveColors()[1]);
    }

    public static Color backgroundColor() {
        return Color.web(effectiveColors()[0]);
    }

    public static boolean isDark() {
        Color bg = backgroundColor();
        double luminance = 0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue();
        return luminance < 0.5;
    }

    public static void install() {
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                if (!change.wasAdded()) {
                    continue;
                }
                for (Window added : change.getAddedSubList()) {
                    watch(added);
                }
            }
        });
    }

    public static void applyToAllWindows(String theme) {
        for (Window window : Window.getWindows().stream().toList()) {
            apply(window.getScene(), theme);
        }
    }

    public static void apply(Scene scene, String theme) {
        if (scene == null) {
            return;
        }
        scene.getStylesheets().clear();
        if (previewBackground != null && previewText != null) {
            String previewCss = CustomColorTheme.buildStylesheet(previewBackground, previewText);
            if (previewCss != null) {
                scene.getStylesheets().add(previewCss);
                return;
            }
        }
        AppSettings settings = AppSettings.getInstance();
        if (settings.isCustomColorActive()) {
            String customCss = CustomColorTheme.buildStylesheet(
                    settings.getCustomBackgroundColor(), settings.getCustomTextColor());
            if (customCss != null) {
                scene.getStylesheets().add(customCss);
                return;
            }
        }
        if (AppSettings.THEME_DARK.equals(theme)) {
            String url = LogistikApplication.class.getResource(DARK_STYLESHEET) != null
                    ? LogistikApplication.class.getResource(DARK_STYLESHEET).toExternalForm()
                    : null;
            if (url != null) {
                scene.getStylesheets().add(url);
            }
        }
    }

    private static void watch(Window window) {
        String theme = AppSettings.getInstance().getTheme();
        apply(window.getScene(), theme);
        if (window.getProperties().put(WATCHED_KEY, Boolean.TRUE) == null) {
            window.sceneProperty().addListener((obs, oldScene, newScene) ->
                    apply(newScene, AppSettings.getInstance().getTheme()));
        }
    }
}
