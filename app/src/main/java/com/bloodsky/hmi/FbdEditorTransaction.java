package com.bloodsky.hmi;

import java.util.ArrayList;
import java.util.List;

/** Snapshot before mutation; failed synchronous publication restores independent copies. */
public final class FbdEditorTransaction {
    public interface Commit { boolean save(); }
    private FbdEditorTransaction() {}
    public static boolean apply(List<MainActivity.Widget> widgets,List<MainActivity.FbdConnection> edges,
                                Runnable mutation,Commit commit) throws Exception {
        List<MainActivity.Widget> oldWidgets=new ArrayList<>();
        List<MainActivity.FbdConnection> oldEdges=new ArrayList<>();
        for(MainActivity.Widget widget:widgets)oldWidgets.add(MainActivity.copyWidget(widget));
        for(MainActivity.FbdConnection edge:edges)oldEdges.add(MainActivity.connectionFromJson(MainActivity.connectionToJson(edge)));
        try { mutation.run(); if(commit.save())return true; }
        catch(RuntimeException failed) { /* Restore below, including partially applied mutations. */ }
        widgets.clear(); widgets.addAll(oldWidgets); edges.clear(); edges.addAll(oldEdges);
        return false;
    }
}
