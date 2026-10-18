import { Video } from "@remotion/media";
import { AbsoluteFill, Composition, Series, staticFile, useVideoConfig } from "remotion";

const Edited = () => {
  const { fps } = useVideoConfig();
  return (
    <AbsoluteFill style={{ backgroundColor: "black" }}>
      <Series>
        <Series.Sequence name="桌面替换" durationInFrames={224} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            trimBefore={87}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
        </Series.Sequence>
        <Series.Sequence name="底层修改与效果" durationInFrames={225} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            trimBefore={639}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
        </Series.Sequence>
        <Series.Sequence name="原生设置" durationInFrames={79} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            trimBefore={1056}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
        </Series.Sequence>
        <Series.Sequence name="精简口头修正" durationInFrames={56} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            trimBefore={1170}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
        </Series.Sequence>
        <Series.Sequence name="电视原生系统" durationInFrames={150} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            trimBefore={1308}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
        </Series.Sequence>
        <Series.Sequence name="壁纸来源" durationInFrames={211} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            trimBefore={2051}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
        </Series.Sequence>
        <Series.Sequence name="功能保持简单" durationInFrames={154} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            trimBefore={2289}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
        </Series.Sequence>
        <Series.Sequence name="应用隐藏和删除" durationInFrames={307} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            trimBefore={2519}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
        </Series.Sequence>
        <Series.Sequence name="飞牛应用演示" durationInFrames={168} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            trimBefore={3030}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
        </Series.Sequence>
        <Series.Sequence name="老人孩子易操作" durationInFrames={270} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            trimBefore={3342}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
        </Series.Sequence>
        <Series.Sequence name="邀请建议" durationInFrames={248} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            trimBefore={4155}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
        </Series.Sequence>
      </Series>
    </AbsoluteFill>
  );
};

const Original = () => {
  const { fps } = useVideoConfig();
  return (
    <AbsoluteFill style={{ backgroundColor: "black" }}>
      <Video
        name="原始录制"
        src={staticFile("source.mov")}
        durationInFrames={4458}
        premountFor={fps}
        objectFit="contain"
        style={{ width: "100%", height: "100%" }}
      />
    </AbsoluteFill>
  );
};

export const MyComposition = () => (
  <>
    <Composition
      id="Edited"
      component={Edited}
      durationInFrames={2092}
      fps={60}
      width={1080}
      height={1920}
    />
    <Composition
      id="Original"
      component={Original}
      durationInFrames={4458}
      fps={60}
      width={1080}
      height={1920}
    />
  </>
);
