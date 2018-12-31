package com.waben.stock.futuresgateway.yisheng.server.ws;

import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import com.waben.stock.futuresgateway.yisheng.common.protobuf.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.netty.buffer.Unpooled.wrappedBuffer;

@Component("serverChannelInitializerForWs")
public class ServerChannelInitializer extends ChannelInitializer<SocketChannel> {
	private final static int READER_IDLE_TIME_SECONDS = 600;//读操作空闲20秒
	private final static int WRITER_IDLE_TIME_SECONDS = 600;//写操作空闲20秒
	private final static int ALL_IDLE_TIME_SECONDS = 1200;//读写全部空闲40秒

    
    @Autowired
    @Qualifier("logicServerHandlerForWs")
    private ChannelInboundHandlerAdapter logicServerHandler;

    
    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        ChannelPipeline pipeline = socketChannel.pipeline();

        // HTTP请求的解码和编码
        pipeline.addLast(new HttpServerCodec());
        // 把多个消息转换为一个单一的FullHttpRequest或是FullHttpResponse，
        // 原因是HTTP解码器会在每个HTTP消息中生成多个消息对象HttpRequest/HttpResponse,HttpContent,LastHttpContent
        pipeline.addLast(new HttpObjectAggregator(65536));
        // 主要用于处理大数据流，比如一个1G大小的文件如果你直接传输肯定会撑暴jvm内存的; 增加之后就不用考虑这个问题了
        pipeline.addLast(new ChunkedWriteHandler());
        // WebSocket数据压缩
//        pipeline.addLast(new WebSocketServerCompressionHandler());
        // 协议包长度限制
        pipeline.addLast(new WebSocketServerProtocolHandler("/ws", null, true, 1024 * 10));
        // 协议包解码
        pipeline.addLast(new MessageToMessageDecoder<WebSocketFrame>() {
            @Override
            protected void decode(ChannelHandlerContext ctx, WebSocketFrame frame, List<Object> objs) throws Exception {
                ByteBuf buf = ((BinaryWebSocketFrame) frame).content();
                objs.add(buf);
                buf.retain();
            }
        });
        // 协议包编码
        pipeline.addLast(new MessageToMessageEncoder<MessageLiteOrBuilder>() {
            @Override
            protected void encode(ChannelHandlerContext ctx, MessageLiteOrBuilder msg, List<Object> out) throws Exception {
                ByteBuf result = null;
                if (msg instanceof MessageLite) {
                    result = wrappedBuffer(((MessageLite) msg).toByteArray());
                }
                if (msg instanceof MessageLite.Builder) {
                    result = wrappedBuffer(((MessageLite.Builder) msg).build().toByteArray());
                }

                // ==== 上面代码片段是拷贝自TCP ProtobufEncoder 源码 ====
                // 然后下面再转成websocket二进制流，因为客户端不能直接解析protobuf编码生成的

                WebSocketFrame frame = new BinaryWebSocketFrame(result);
                out.add(frame);
            }
        });


        //配置Protobuf解码处理器，消息接收到了就会自动解码，ProtobufDecoder是netty自带的，Message是自己定义的Protobuf类
        pipeline.addLast("protobufDecoder",
                new ProtobufDecoder(Message.MessageBase.getDefaultInstance()));






        pipeline.addLast("logicServerHandler", logicServerHandler);

    }
}
