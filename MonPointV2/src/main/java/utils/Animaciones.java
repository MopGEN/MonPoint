package utils;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.util.Duration;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;

public class Animaciones {

    public static void fadeIn(Node nodo, double duracionMs) {
        FadeTransition ft = new FadeTransition(Duration.millis(duracionMs), nodo);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.setInterpolator(Interpolator.EASE_BOTH);
        ft.play();
    }

    public static void fadeOut(Node nodo, double duracionMs) {
        FadeTransition ft = new FadeTransition(Duration.millis(duracionMs), nodo);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setInterpolator(Interpolator.EASE_BOTH);
        ft.play();
    }

    public static void zoomIn(Node nodo, double duracionMs) {
        nodo.setScaleX(0.92);
        nodo.setScaleY(0.92);
        ScaleTransition st = new ScaleTransition(Duration.millis(duracionMs), nodo);
        st.setToX(1.0);
        st.setToY(1.0);
        st.setInterpolator(Interpolator.EASE_OUT);
        st.play();
    }

    public static void zoomOut(Node nodo, double duracionMs) {
        ScaleTransition st = new ScaleTransition(Duration.millis(duracionMs), nodo);
        st.setToX(0.92);
        st.setToY(0.92);
        st.setInterpolator(Interpolator.EASE_IN);
        st.play();
    }

    public static void slideInFromRight(Node nodo, double duracionMs) {
        nodo.setTranslateX(300);
        TranslateTransition tt = new TranslateTransition(Duration.millis(duracionMs), nodo);
        tt.setToX(0);
        tt.setInterpolator(Interpolator.EASE_BOTH);
        tt.play();
    }

    public static void rippleEffect(Node nodo) {
        DropShadow ripple = new DropShadow();
        ripple.setColor(Color.web("#41504d", 0.3));
        ripple.setRadius(20);
        nodo.setEffect(ripple);

        FadeTransition ft = new FadeTransition(Duration.millis(400), nodo);
        ft.setFromValue(0.8);
        ft.setToValue(1.0);
        ft.setCycleCount(1);
        ft.play();

        Timeline removeEffect = new Timeline(new KeyFrame(Duration.millis(450), e -> nodo.setEffect(null)));
        removeEffect.play();
    }

    public static void entradaSuave(Node nodo) {
    nodo.setOpacity(0);
    nodo.setTranslateY(15);

    KeyValue kvOpacity = new KeyValue(nodo.opacityProperty(), 1.0, Interpolator.EASE_OUT);
    KeyValue kvTranslate = new KeyValue(nodo.translateYProperty(), 0.0, Interpolator.EASE_OUT);

    KeyFrame kf = new KeyFrame(Duration.millis(500), kvOpacity, kvTranslate);
    Timeline timeline = new Timeline(kf);
    timeline.setCycleCount(1);
    timeline.play();
}

}
