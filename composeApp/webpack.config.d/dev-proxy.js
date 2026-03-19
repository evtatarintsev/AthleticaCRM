// Проксирует /api на backend-сервер в режиме разработки.
// Браузер видит запросы как same-origin → cookie устанавливаются и отправляются автоматически.
config.devServer = config.devServer || {};
config.devServer.proxy = [
    {
        context: ["/api"],
        target: "http://localhost:8080",
        changeOrigin: true,
    },
];
