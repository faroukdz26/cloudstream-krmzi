// Cloudstream JavaScript Provider
function KrmziProvider() {
    this.name = "قرمزي (Krmzi)";
    this.baseUrl = "https://krmzi.org";
    this.lang = "ar";

    this.search = async function(query) {
        const response = await request(`${this.baseUrl}/?s=${encodeURIComponent(query)}`);
        const html = response.text;
        const results = [];
        // منطق جلب نتائج البحث من الهيكل
        const regex = /<article[^>]*>[\s\S]*?href="([^"]+)"[^>]*title="([^"]+)"[\s\S]*?src="([^"]+)"/g;
        let match;
        while ((match = regex.exec(html)) !== null) {
            results.push({
                title: match[2].replace("- قرمزي", "").trim(),
                url: match[1],
                poster: match[3],
                type: 1
            });
        }
        return results;
    };

    this.load = async function(url) {
        const response = await request(url);
        const html = response.text;
        const isEp = url.includes('/episode/');
        
        let titleMatch = html.match(/<h1>([^<]+)<\/h1>/);
        let title = titleMatch ? titleMatch[1].trim() : "عنوان غير معروف";
        
        if (!isEp) {
            const episodes = [];
            const epRegex = /<a[^>]*href="([^"]+?\/episode\/[^"]+)"[^>]*>([\s\S]*?)<\/a>/g;
            let match;
            while ((match = epRegex.exec(html)) !== null) {
                episodes.push({
                    name: match[2].replace(/<[^>]*>/g, "").trim() || "حلقة",
                    url: match[1]
                });
            }
            return { title, url, type: 2, episodes: episodes.reverse() };
        } else {
            return { title, url, type: 1, episodes: [{ name: title, url: url }] };
        }
    };

    this.loadLinks = async function(url, callback) {
        const response = await request(url);
        const html = response.text;
        
        // استخراج سيرفرات الـ iframe المباشرة
        const iframeRegex = /<iframe[^>]*src="([^"]+)"/g;
        let match;
        while ((match = iframeRegex.exec(html)) !== null) {
            let src = match[1];
            if (!src.includes("ads")) {
                callback({
                    url: src,
                    name: src.includes("larhu") ? "Larhu" : "سيرفر مباشر",
                    isEmbed: true
                });
            }
        }
    };
}

module.exports = KrmziProvider;
