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

    get_shorts_id = (url) => {
        try {
            const match = url.match(/shorts\/([^&#]+)/)
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

    observe_shorts_id = () => {
        const current_shorts_id = get_shorts_id(location.href)
        if (current_shorts_id && window.shorts_id !== current_shorts_id) {
            window.shorts_id = current_shorts_id
            window.dispatchEvent(new Event('onShortsIdChange'))
        }
    }

    window.addEventListener('onProgressChangeFinish', observe_shorts_id)


    // Wait for the specific element to be loaded.
    // ref: https://stackoverflow.com/questions/5525071/how-to-wait-until-an-element-exists
    waitElem = (selector) => {
        return new Promise(resolve => {
            if (document.querySelector(selector)) {
                return resolve(document.querySelector(selector))
            }
    
            const observer = new MutationObserver( () => {
                if (document.querySelector(selector)) {
                    observer.disconnect()
                    resolve(document.querySelector(selector))
                }
            })
    
            observer.observe(document.body, {
                childList: true,
                subtree: true
            })
        })
    }

    /*
    // format number, eg: 1111 => 1.1K
    formatNumber = (num) => {
        const units = ['K', 'M', 'B', 'T']
        let i = -1
        while (num >= 1000 && i < units.length - 1) {
          num /= 1000
          i++
        }
        if (i === -1) {
            return num
        }
        return num.toFixed(1) + units[i]
      }
    // return dislike counts
    add_dislike_counts = () => {
        const pageClass = get_page_class(location.href)
        if (pageClass === 'watch') {
            fetch(`https://returnyoutubedislikeapi.com/votes?videoId=${get_video_id(location.href)}`)
            .then((response) => {
                if (!response.ok) {
                    throw new Error('Network response was not ok')
                }
                return response.json()
            }).then((json) => {
            let dislike = json.dislikes
            waitElem('dislike-button-view-model').then((dislikeView) => {
                let dislikeText = document.createElement('div')
                dislikeText.classList.add('yt-spec-button-shape-next__button-text-content')
                dislikeText.innerText = formatNumber(dislike)
                dislikeText.style.marginLeft = '6px'
                dislikeView.querySelector('yt-touch-feedback-shape').appendChild(dislikeText)
                dislikeView.querySelector('button').style.width = 'auto'
        })
        })}
        if (pageClass === 'shorts') {
            fetch(`https://returnyoutubedislikeapi.com/votes?videoId=${get_shorts_id(location.href)}`)
            .then((response) => {
                if (!response.ok) {
                    throw new Error('Network response was not ok')
                }
                return response.json()
            }).then((json) => {
                let dislike = json.dislikes
                waitElem('button[aria-label="Dislike this video"]').then((dislikeView) => {
                    dislikeView.parentElement.querySelector('span[role="text"]').innerText = formatNumber(dislike)
                })
            })
        }
        
        
    }

    window.addEventListener('onVideoIdChange', add_dislike_counts)
    window.addEventListener('onShortsIdChange', add_dislike_counts)
    */


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
        const page_class = get_page_class(location.href)
        if (page_class === 'home' || page_class === 'subscriptions' || page_class === 'library'
            || page_class === '@'
        ){
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
                    if (node.nodeType !== 1) {
                        return
                    }

                    if (node.classList.contains('video-stream')){

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
                            if([1, 3].includes(data) && window.last_player_state === -1 && get_page_class(location.href) === 'watch'){
                                // resume progress
                                const saved_time = parseInt(localStorage.getItem('progress-' + get_video_id(location.href)) || '0')
                                if (saved_time > 5 && node.getDuration() - saved_time > 5) {
                                    node.seekTo(saved_time)
                                }
                            }
                            window.last_player_state = data
                        })
                    }

                    // add download button
                    let bSave = document.querySelector('.yt-spec-button-view-model.slim_video_action_bar_renderer_button')
                    if(bSave && get_page_class(location.href) === 'watch' && !document.getElementById('downloadButton')) {
                        let bDown = bSave.cloneNode(true)
                        bDown.id = 'downloadButton'
                        bDown.getElementsByClassName("yt-spec-button-shape-next__button-text-content")[0].innerText = values('download')
                        
                        const svg = bDown.querySelector('svg')
                        if (svg) {
                            svg.setAttribute("viewBox", "0 -960 960 960")
                            const path = svg.querySelector('path')
                            if (path) {
                                path.setAttribute("d", "M480-328.46 309.23-499.23l42.16-43.38L450-444v-336h60v336l98.61-98.61 42.16 43.38L480-328.46ZM252.31-180Q222-180 201-201q-21-21-21-51.31v-108.46h60v108.46q0 4.62 3.85 8.46 3.84 3.85 8.46 3.85h455.38q4.62 0 8.46-3.85 3.85-3.84 3.85-8.46v-108.46h60v108.46Q780-222 759-201q-21 21-51.31 21H252.31Z")
                            }
                            bDown.onclick = function() {
                                android.download(get_video_id(location.href))
                            }
                            bSave.parentElement.insertBefore(bDown, bSave)
                        }
                    }
                })
            }
        }
    })
    observer.observe(document.documentElement, {
        childList: true,
        subtree: true
    })

    

    window.injected = true
    
}} catch (error) {
    console.error(error)
    throw error
}

