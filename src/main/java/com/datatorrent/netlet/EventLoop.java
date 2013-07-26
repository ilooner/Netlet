/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.datatorrent.netlet;

import com.datatorrent.netlet.Listener.ClientListener;
import com.datatorrent.netlet.Listener.ServerListener;

import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * <p>EventLoop interface.</p>
 *
 * @author Chetan Narsude <chetan@datatorrent.com>
 * @since 0.3.2
 */
public interface EventLoop
{
  void connect(final InetSocketAddress address, final Listener l);

  void disconnect(final ClientListener l);

  void register(ServerSocketChannel channel, Listener l);

  void register(SocketChannel channel, int ops, Listener l);

  void start(final String host, final int port, final ServerListener l);

  void stop(final ServerListener l);

  void submit(Runnable r);

  void unregister(final SelectableChannel c);

}