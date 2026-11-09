package com.example.itrain_logistik;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Set;

/**
 * Rechte Seite der Lok-/Wagen-/Block-Fenster: frei benannte Zusatz-
 * Datenfelder ({@link CustomField}), bearbeitbar, mit "Hinzufuegen"/
 * "Entfernen". Die inzwischen strukturiert abgefragten Felder (siehe
 * {@link LogistikFelder}, {@link WagonLogistikPane}, {@link LocomotiveLogistikPane},
 * {@link BlockLogistikPane}) sind technisch weiterhin ganz normale
 * CustomFields - damit sie hier nicht doppelt als frei bearbeitbare
 * Name/Wert-Zeile auftauchen, kann eine Menge auszublendender Feldnamen
 * uebergeben werden. Die Tabelle zeigt dann nur die restlichen (wirklich
 * freien) Felder, "Hinzufuegen"/"Entfernen" arbeiten aber weiterhin auf der
 * echten, kompletten Liste.
 */
public final class CustomFieldsPane extends VBox {

    public CustomFieldsPane(ObservableList<CustomField> fields, Runnable onChanged) {
        this(fields, Set.of(), onChanged);
    }

    public CustomFieldsPane(ObservableList<CustomField> fields, Set<String> hiddenNames, Runnable onChanged) {
        setSpacing(8);
        setPadding(new Insets(10));
        setPrefWidth(280);

        FilteredList<CustomField> sichtbar = new FilteredList<>(fields,
                f -> hiddenNames == null || !hiddenNames.contains(f.getName()));

        TableView<CustomField> table = new TableView<>(sichtbar);
        Spalten.einrichten(table, "zusatzfelder");
        table.setEditable(true);
        table.setPrefHeight(240);

        TableColumn<CustomField, String> nameColumn = new TableColumn<>("Feld");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        nameColumn.setOnEditCommit(e -> {
            e.getRowValue().setName(e.getNewValue());
            onChanged.run();
        });
        nameColumn.setPrefWidth(120);

        TableColumn<CustomField, String> valueColumn = new TableColumn<>("Wert");
        valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
        valueColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        valueColumn.setOnEditCommit(e -> {
            e.getRowValue().setValue(e.getNewValue());
            onChanged.run();
        });
        valueColumn.setPrefWidth(140);

        table.getColumns().setAll(java.util.List.of(nameColumn, valueColumn));

        Button addButton = new Button("Hinzufügen");
        addButton.setOnAction(e -> {
            fields.add(new CustomField("Neues Feld", ""));
            onChanged.run();
        });
        Button removeButton = new Button("Entfernen");
        removeButton.setOnAction(e -> {
            CustomField selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                fields.remove(selected);
                onChanged.run();
            }
        });
        HBox buttons = new HBox(8, addButton, removeButton);
        buttons.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(new Label("Eigene Datenfelder"), table, buttons);
    }
}
