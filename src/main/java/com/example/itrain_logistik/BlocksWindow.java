package com.example.itrain_logistik;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import com.example.itrain_logistik.kern.Anlage;

import java.util.List;

/**
 * Eigenes Fenster "Blöcke": Liste links (name/type), rechts eigene Datenfelder und die Zuordnung
 * zu einem Gleis der Anlage (ersetzt den frueheren "Logistik-Blocktyp"; Gleise werden im
 * Anlagen-Editor an der Station zugeordnet).
 */
public final class BlocksWindow {

    private static Stage stage;

    private BlocksWindow() {
    }

    public static void show(Stage owner, List<BlockEntry> blocks, Anlage anlage, Runnable onDataChanged) {
        if (stage != null && stage.isShowing()) {
            rebuild(blocks, anlage, onDataChanged);
            stage.toFront();
            stage.requestFocus();
            return;
        }
        stage = new Stage();
        stage.setTitle("Blöcke");
        stage.initOwner(owner);
        rebuild(blocks, anlage, onDataChanged);
        WindowState.apply(stage, "blocks", 700, 480);
        stage.show();
    }

    private static void rebuild(List<BlockEntry> blocks, Anlage anlage, Runnable onDataChanged) {
        ListView<BlockEntry> list = new ListView<>(FXCollections.observableArrayList(blocks));
        list.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(BlockEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item.getName() + (item.getType() != null && !item.getType().isBlank()
                        ? "  (" + item.getType() + ")" : ""));
            }
        });

        VBox detail = new VBox(10);
        detail.setPadding(new Insets(10));
        detail.setPrefWidth(320);
        Label placeholder = new Label("Block links auswählen.");
        detail.getChildren().add(placeholder);

        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            detail.getChildren().clear();
            if (newV == null) {
                detail.getChildren().add(placeholder);
                return;
            }
            Label name = new Label("Name: " + newV.getName());
            Label type = new Label("Typ: " + (newV.getType() != null && !newV.getType().isBlank()
                    ? newV.getType() : "-"));
            Anlage.GleisTreffer treffer = anlage != null ? anlage.gleisFuerBlock(newV.getName()) : null;
            Label logistikPane = new Label(treffer != null
                    ? "Gleis: " + treffer.station().getAnzeigeName() + " – " + treffer.gleis().getAnzeigeName()
                    + " (" + treffer.gleis().getArt().getLabel() + ")"
                    : "Keinem Gleis zugeordnet (Strecke).");
            CustomFieldsPane fieldsPane = new CustomFieldsPane(newV.getCustomFields(),
                    LogistikFelder.STRUKTURIERTE_BLOCK_FELDER, onDataChanged);
            detail.getChildren().addAll(name, type, logistikPane, fieldsPane);
        });

        SplitPane split = new SplitPane(list, detail);
        split.setDividerPositions(0.4);

        Fenster.szene(stage, split, 700, 480);
        if (!blocks.isEmpty()) {
            list.getSelectionModel().selectFirst();
        }
    }

    public static void refreshIfOpen(java.util.List<BlockEntry> blocks, Anlage anlage, Runnable onDataChanged) {
        if (stage != null && stage.isShowing()) {
            rebuild(blocks, anlage, onDataChanged);
        }
    }

    public static void closeIfOpen() {
        if (stage != null) {
            stage.close();
            stage = null;
        }
    }
}
