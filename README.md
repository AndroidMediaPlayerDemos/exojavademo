# exojavademo

androidx.media3.exoplayer.ExoPlayer example

## 流媒体协议

流媒体传输协议用于在网络上有效地传输音频、视频和其他多媒体内容。这些协议各有特点，适用于不同的使用场景。下面是一些主要的流媒体传输协议及其典型应用场景：

### 1. RTMP（Real-Time Messaging Protocol）

- **擅长场景**：直播流媒体传输。RTMP主要用于向流媒体服务器推送直播内容，然后由服务器分发给观众。尽管HTML5和其他技术的兴起使得RTMP的使用有所下降，但它在直播领域仍然广泛使用，特别是在需要低延迟传输的场合。

### 2. RTSP（Real Time Streaming Protocol）

- **擅长场景**：实时监控和点播服务。RTSP常用于安全监控系统、视频会议以及点播系统，允许用户控制播放操作，如暂停、快进和倒带。

### 3. HLS（HTTP Live Streaming）

- **擅长场景**：跨平台直播和点播。HLS是苹果公司开发的一种协议，通过HTTP传输分段的媒体文件。它支持动态比特率适配，能够根据用户的网络状况自动调整视频质量。HLS广泛用于互联网视频播放，尤其是在移动设备和网页上。

### 4. DASH（Dynamic Adaptive Streaming over HTTP）

- **擅长场景**：提供灵活的跨平台适配性。DASH是一个国际标准，类似于HLS，支持通过HTTP传输分段的视频内容，并能够根据网络条件动态调整质量。DASH广泛应用于OTT（Over The Top）服务和网络视频播放。

### 5. MPEG-TS（MPEG Transport Stream）

- **擅长场景**：数字电视广播和其他广播应用。MPEG-TS是一种标准化的数字容器格式，用于传输和存储音视频数据。它设计用于在错误率较高的通信环境中可靠地传输流媒体，如卫星和有线电视网络。

### 6. WebRTC（Web Real-Time Communication）

- **擅长场景**：实时通信。WebRTC是一个开放标准，允许网页浏览器之间进行直接的音视频通信，无需任何插件或外部应用。它广泛应用于视频聊天、会议和实时直播领域。

### 结论

选择合适的流媒体传输协议取决于特定的应用需求，包括所需的延迟、兼容性、网络条件和内容保护要求。随着技术的发展，新的协议和改进不断出现，为开发者和内容提供者提供了更多的选择和灵活性。

## Azure视频播放方案

Azure Media Services 可以与 SharePoint 配合使用，尽管这两个服务在微软的生态系统中服务于不同的目的，但它们可以一起工作以提供强大的视频内容管理和分发解决方案。SharePoint 主要用于文档管理和协作，而 Azure Media Services 提供视频编码、加密、流式传输和分析等服务。将这两者结合使用，可以提高组织内部和对外发布的视频内容的管理效率和观看体验。

### 配合使用的场景示例

- **企业内部培训**：通过 SharePoint 管理和分享培训材料，包括视频内容。Azure Media Services 可以用于优化视频的格式和质量，确保所有设备上都能顺畅播放。
- **视频资料库**：在 SharePoint 中创建一个视频资料库，用于存储和组织公司的视频资源，如会议录像、产品演示和市场活动。Azure Media Services 可以提供背后的视频处理和流式传输功能。
- **企业通讯**：利用 SharePoint 的协作和文档管理能力来组织和发布公司新闻、更新和通讯，其中包括视频内容。Azure Media Services 保证视频内容的高质量播放和分发。
  
### 实现步骤简述

- **视频上传和存储**：首先，用户需要将视频文件上传到 Azure Media Services，进行编码和处理。这些视频文件可以存储在 Azure Blob 存储中，与 Azure Media Services 紧密集成。
- **视频处理**：在 Azure Media Services 中，视频内容将被编码为多种格式，以支持不同设备和带宽条件下的播放。此外，还可以应用其他视频处理功能，如内容保护和智能分析。
- **集成 SharePoint**：视频处理完成后，可以将视频的播放链接或嵌入代码添加到 SharePoint 页面、文档或列表中。这样，用户就可以直接在 SharePoint 的环境中访问和播放视频内容。
- **权限和访问控制**：可以利用 SharePoint 的权限管理功能来控制谁可以访问和观看视频内容，确保信息安全。

### 注意事项

- **安全性**：确保在视频处理和传输过程中使用加密和安全措施，保护敏感内容不被未经授权的用户访问。
- **兼容性**：在将视频嵌入 SharePoint 时，确保视频格式和播放器与用户的浏览器和设备兼容。

通过将 Azure Media Services 的视频处理能力与 SharePoint 的文档管理和协作功能结合，组织可以构建一个强大的视频内容管理和分发平台，提升内部沟通效率和外部内容发布的影响力。

## ExoPlayer 和 SharePoint

使用安卓的ExoPlayer直接播放SharePoint上的视频时，所用的流媒体协议主要取决于视频的托管和传输方式。SharePoint本身并不专注于流媒体协议，而是一个文档管理和协作平台，因此，视频文件通常以静态文件的形式存储在SharePoint中。

当使用ExoPlayer播放SharePoint上的视频时，最常见的情况是通过HTTP或HTTPS协议进行视频文件的下载或流式传输。这里的关键点是，视频文件被视为普通的网络资源，ExoPlayer通过网络URL获取并播放视频。这种方式并不涉及到专门的流媒体传输协议（如HLS、DASH、RTMP等），而是基于HTTP(S)的简单文件访问。

### 关键点

- **HTTP/HTTPS**：基于HTTP(S)的视频文件访问是最基础的方式，适用于从SharePoint下载或流式传输视频。这种方法的优点是简单易行，但可能不支持复杂的流媒体功能，如自适应比特率流（ABR）。

- **动态流媒体协议**：如果你希望实现更高级的流媒体播放功能（如自适应流、直播等），可能需要将视频托管到支持这些功能的流媒体服务器或服务上（例如Azure Media Services），然后通过支持的流媒体协议（如HLS或DASH）来播放视频。在这种情况下，ExoPlayer可以直接播放HLS或DASH等格式的流媒体内容。

因此，直接从SharePoint播放视频主要是通过HTTP(S)协议，而不是专门的流媒体协议。如果有更高级的播放需求，可能需要考虑其他的视频托管和传输解决方案。

## 流媒体协议和http协议

使用专门的流媒体协议（如HLS、DASH）相比于直接通过HTTP/HTTPS下载和播放视频文件，通常可以提供更平滑、更优化的观看体验，尤其是在网络条件变化或不理想的情况下。这得益于流媒体协议设计中的一些关键特性：

### 1. 自适应比特率流（Adaptive Bitrate Streaming, ABR）

流媒体协议如HLS和DASH支持自适应比特率流。这意味着视频内容被编码成多个不同的质量级别，播放器可以根据当前的网络条件实时选择最合适的质量级别进行播放。这样可以在保持播放不中断的同时，自动调整视频质量，提供尽可能好的观看体验。

### 2. 网络效率

流媒体协议通过优化数据传输和减少缓冲需求来提高网络效率。例如，它们可以只下载当前播放所需的视频片段，而不是整个文件，这样可以更快地开始播放，并根据用户的观看进度动态加载内容。

### 3. 错误恢复

在网络不稳定或出现数据丢失的情况下，流媒体协议能够更有效地处理错误和重新请求数据。这有助于减少播放中断和提高播放质量。

### 4. 直播支持

专门的流媒体协议提供了对直播内容的原生支持，包括低延迟直播和大规模分发。这是通过HTTP/HTTPS直接下载视频文件难以实现的。

结论，尽管通过HTTP/HTTPS播放视频在技术上更简单、更直接，适用于一些基本的用例，但在需要高质量、高效率和更好用户体验的场景下（如直播、长视频内容、移动环境等），使用专门的流媒体协议会提供显著更好的结果。流媒体协议通过自适应比特率流、优化的网络传输和强大的错误恢复机制等特性，能够确保在各种网络条件下都能提供平滑且高质量的播放体验。

## FAQ

- [播放器事件](https://developer.android.com/media/media3/exoplayer/listening-to-player-events?hl=zh-cn)
- [轨道选择](https://developer.android.com/media/media3/exoplayer/track-selection?hl=zh-cn#java)
- [演示应用](https://developer.android.com/media/media3/exoplayer/demo-application?hl=zh-cn)
- [Simplified bandwidth meter usage](https://medium.com/google-exoplayer/https-medium-com-google-exoplayer-simplified-bandwidth-meter-usage-17d8189f978b)
