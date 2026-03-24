FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# 创建包目录结构
RUN mkdir -p qzrs/Scrcpy/relay

COPY RelayServer.java qzrs/Scrcpy/relay/

# 编译
WORKDIR /app
RUN javac qzrs/Scrcpy/relay/RelayServer.java

# 暴露端口
EXPOSE 3478

# 运行
CMD ["java", "qzrs.Scrcpy.relay.RelayServer", "3478"]
