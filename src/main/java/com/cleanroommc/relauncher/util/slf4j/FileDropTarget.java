package com.cleanroommc.relauncher.util;

import com.cleanroommc.relauncher.CleanroomRelauncher;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class FileDropTarget implements DropTargetListener {

    private boolean drop = false;
    private final List<File> files = new ArrayList<>();

    private final Predicate<File> predicate;
    private final Consumer<List<File>> consumer;

    public FileDropTarget(Predicate<File> predicate, Consumer<List<File>> consumer) {
        this.predicate = predicate;
        this.consumer = consumer;
    }

    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
        drop = false;
        files.clear();
        Transferable t = dtde.getTransferable();
        if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            try {
                Object td = t.getTransferData(DataFlavor.javaFileListFlavor);
                if (td instanceof List) {
                    drop = true;
                    for (Object value : ((List) td)) {
                        if (value instanceof File) {
                            File file = (File) value;
                            if (!predicate.test(file)) {
                                drop = false;
                                break;
                            } else {
                                files.add(file);
                            }
                        }
                    }
                }
            } catch (UnsupportedFlavorException | IOException ex) {
                CleanroomRelauncher.LOGGER.info(ex);
            }
        }
        if (drop) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
        } else {
            dtde.rejectDrag();
        }
    }

    @Override
    public void dragOver(DropTargetDragEvent dtde) {

    }

    @Override
    public void dropActionChanged(DropTargetDragEvent dtde) {

    }

    @Override
    public void dragExit(DropTargetEvent dte) {
        drop = false;
        files.clear();
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
        consumer.accept(files);
        drop = false;
        files.clear();
    }
}