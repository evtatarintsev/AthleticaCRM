// Проксирует /api на backend-сервер в режиме разработки.
// Браузер видит запросы как same-origin → cookie устанавливаются и отправляются автоматически.
//
// allowedHosts: "all" — разрешает доступ по IP (192.168.x.x) и любому hostname.
// По умолчанию webpack-dev-server 4+ блокирует запросы с Host != localhost
// (защита от DNS-rebinding), из-за чего при открытии по IP возвращается 403
// ещё до того, как прокси успевает сработать.
//
// headers.origin — переписываем Origin перед отправкой в Ktor.
// changeOrigin меняет только Host, но не Origin. Ktor проверяет именно Origin,
// и при доступе через IP (Origin: http://192.168.x.x:8081) отвечает 403,
// т.к. CORS разрешает только localhost:8081.
config.devServer = config.devServer || {};
config.devServer.allowedHosts = "all";
config.devServer.proxy = [
    {
        context: ["/api"],
        target: "http://localhost:8080",
        changeOrigin: true,
        headers: {
            origin: "http://localhost:8081",
        },
    },
];
