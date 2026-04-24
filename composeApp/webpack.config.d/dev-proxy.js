// allowedHosts: "all" — разрешает запросы от athletica.crm и любого другого хоста.
// По умолчанию webpack-dev-server 4+ блокирует Host != localhost (защита от DNS-rebinding).
config.devServer = config.devServer || {};
config.devServer.port = 3000;
config.devServer.allowedHosts = "all";
config.devServer.historyApiFallback = true;
