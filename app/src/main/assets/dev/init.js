try {

    // avoid repeated injection
    if (!window.injected){

    // Utils
    values = (key) => {
        const languages = {
            'zh': { 'loop': '循环播放', 'download': '下载', 'ok': '确定', 'video': '视频', 'cover': '封面' },
            'en': { 'loop': 'Loop Play', 'download': 'Download', 'ok': 'OK', 'video': 'Video', 'cover': 'Cover' },
            'ja': { 'loop': 'ループ再生', 'download': 'ダウンロード', 'ok': 'はい', 'video': 'ビデオ', 'cover': 'カバー' },
            'ko': { 'loop': '반복 재생', 'download': '시모타코', 'ok': '확인', 'video': '비디오', 'cover': '커버' },
            'fr': { 'loop': 'Lecture en boucle', 'download': 'Télécharger', 'ok': "D'accord", 'video': 'vidéo', 'cover': 'couverture' },
            }
            
        const lang = (document.body.lang || 'en').substring(0, 2).toLowerCase()
        return languages[lang] ? languages[lang][key] : languages['en'][key]
    }

    get_page_class = (url) => {
        url = url.toLowerCase()
        if (url.startsWith('https://m.youtube.com')) {
            if (url.includes('shorts')) {
                return 'shorts'
            }
            if (url.includes('watch')) {
                return 'watch'
            }
            if (url.includes('library')) {
                return 'library'
            }
            if (url.includes('subscriptions')) {
                return 'subscriptions'
            }
            if (url.includes('@')) {
                return '@'
            }
            return 'home'
        }
        return 'unknown'
    }


    get_video_id = (url) => {
        try {
            const match = url.match(/watch\?v=([^&#]+)/)
            return match ? match[1] : null
        } catch (error) {
            console.error('Error getting video ID:', error)
            return null
        }
    }

    // observe video id change

    // using onProgressChangeFinish event in android
    observe_video_id = () => {
        const current_video_id = get_video_id(location.href)
        if (current_video_id && window.video_id !== current_video_id) {
            window.video_id = current_video_id
            window.dispatchEvent(new Event('onVideoIdChange'))
        }
    }
    
    window.addEventListener('onProgressChangeFinish', observe_video_id)


    // Download
    if (window.interval_id_download){
        clearInterval(window.interval_id_download)
    }
    window.interval_id_download = setInterval(()=>{
        const bReport = document.querySelector('button-view-model.slim_video_action_bar_renderer_button:nth-child(4)')
        if (!bReport) {
            return
        }
        if(get_page_class(location.href) !== 'watch') {
            return
        }
        if(document.getElementById('downloadButton') === null) {
            let bParent = bReport.parentElement
            let bDown = bReport.cloneNode(true)
            bDown.id = 'downloadButton'     
            bDown.getElementsByClassName("yt-spec-button-shape-next__button-text-content")[0].innerText = values('download')
            
            let dSvg = document.createElementNS("http://www.w3.org/2000/svg", "svg")
            dSvg.setAttribute("xmlns", "http://www.w3.org/2000/svg")
            dSvg.setAttribute("height", "24px")
            dSvg.setAttribute("viewBox", "0 -960 960 960")
            dSvg.setAttribute("width", "24px")
            dSvg.setAttribute("fill", "#5f6368")
            let path = document.createElementNS("http://www.w3.org/2000/svg", "path")
            path.setAttribute("d", "M200-120v-40h560v40H200Zm279.23-150.77L254.62-568.46h130.76V-840h188.47v271.54h130.77L479.23-270.77Zm0-65.38 142.92-191.54h-88.3V-800H425.38v272.31h-88.3l142.15 191.54Zm.77-191.54Z")
            dSvg.appendChild(path)

            bDown.getElementsByTagName("svg")[0].replaceWith(dSvg)

            bDown.onclick = function() {
                android.download(location.href)
            }
            bParent.appendChild(bDown)
        }
    }, 1000)


    // Ads block
    if (!window.originalFetch) {
        window.originalFetch = fetch

        fetch = async (...args) => {
            const url = args[0].url
            if (url && url.includes('youtubei/v1/player')) {
                const response = await window.originalFetch(...args)
                const data = await response.json()

                // Remove specified keys from the response data
                const rules = ['playerAds', 'adPlacements', 'adBreakHeartbeatParams', 'adSlots']
                for (const rule of rules) {
                    if (data.hasOwnProperty(rule)) {
                        delete data[rule]
                    }
                }

                return new Response(JSON.stringify(data), {
                    status: response.status,
                    headers: response.headers,
                })
            } else {
                return window.originalFetch(...args)
            }
        }
    }


    // Init configuration

    // video quality
    set_quality = () => {
        const player = document.getElementById("movie_player")
        if (get_page_class(location.href) === 'watch'){
            const target_quality = localStorage.getItem('video_quality') || 'default'
            if (player.getAvailableQualityLevels().indexOf(target_quality) !== -1){
                player.setPlaybackQualityRange(target_quality)
            }
        }
    }

    save_quality = (e) => {
        const target = e.target
        if (target.id.startsWith('player-quality-dropdown') || target.classList.contains('player-quality-settings')){
            console.log('video quality setting changed')
            localStorage.setItem('video_quality', document.getElementById("movie_player").getPlaybackQuality())
        }
    } 
    
    document.addEventListener('change', save_quality)
    window.addEventListener('onVideoIdChange', set_quality)


    // refresh
    window.addEventListener('onRefresh', () => {
        window.location.reload()
    })
    window.addEventListener('onProgressChangeFinish', () => {
        android.finishRefresh()
    })

    window.addEventListener('doUpdateVisitedHistory', () => {
        const page_clas = get_page_class(location.href)
        if (page_clas === 'home' || page_clas === 'subscriptions'){
            android.setRefreshLayoutEnabled(true)
        } else{
            android.setRefreshLayoutEnabled(false)
        }
    })


    // init video player

    const observer = new MutationObserver((mutationsList) => {
        for (const mutation of mutationsList) {
            if (mutation.type === 'childList') {
                mutation.addedNodes.forEach((node) => {
                    if (node.nodeType === 1 && node.classList.contains('video-stream')){

                        // memory progress
                        node.addEventListener('timeupdate', () => {
                            if (node.currentTime !== 0){
                                localStorage.setItem('progress-' + get_video_id(location.href),
                                node.currentTime.toString())
                            }
                        })
                    }
                    
                    if (node.id === 'movie_player') {
                        window.last_player_state = -1
                        node.addEventListener('onStateChange', (data) => {
                            if(data === 3 && window.last_player_state === -1 && get_page_class(location.href) === 'watch'){
                                // resume progress
                                const saved_time = localStorage.getItem('progress-' + get_video_id(location.href)) || '0'
                                node.seekTo(parseInt(saved_time))
                            }
                            window.last_player_state = data
                        })
                    } 
                })
            }
        }
    })
    observer.observe(document.body, {
        childList: true,
        subtree: true
    })


    window.injected = true
}
} catch (error) {
    console.error(error)
    throw error
}

