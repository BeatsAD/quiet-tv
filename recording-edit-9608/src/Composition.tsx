import { BasicCaptions } from "./basic-captions";
import { Video } from "@remotion/media";
import { AbsoluteFill, Composition, Series, staticFile, useVideoConfig } from "remotion";

const Edited = () => {
  const { fps } = useVideoConfig();
  return (
    <AbsoluteFill style={{ backgroundColor: "black" }}>
      <Series>
        <Series.Sequence name="桌面替换" trimBefore={87} durationInFrames={224} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
          <BasicCaptions
            name="桌面替换字幕"
            premountFor={fps}
            width={940}
            style={{ position: "absolute", bottom: 220, left: 70 }}
            captions={[
              {
                text: "现在已经实现了桌面的替换",
                startMs: 1650,
                endMs: 5080,
                timestampMs: null,
                confidence: null,
                pageBreakAfter: true,
              },
            ]}
          />
        </Series.Sequence>
        <Series.Sequence name="底层修改与效果" trimBefore={639} durationInFrames={225} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
          <BasicCaptions
            name="底层修改与效果字幕"
            premountFor={fps}
            width={940}
            style={{ position: "absolute", bottom: 220, left: 70 }}
            captions={[
              {
                text: "而且是从底层修改的",
                startMs: 10720,
                endMs: 12580,
                timestampMs: null,
                confidence: null,
                pageBreakAfter: true,
              },
              {
                text: "大家看一下效果",
                startMs: 12960,
                endMs: 14200,
                timestampMs: null,
                confidence: null,
                pageBreakAfter: true,
              },
            ]}
          />
        </Series.Sequence>
        <Series.Sequence name="原生设置" trimBefore={1056} durationInFrames={79} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
          <BasicCaptions
            name="原生设置字幕"
            premountFor={fps}
            width={940}
            style={{ position: "absolute", bottom: 220, left: 70 }}
            captions={[
              {
                text: "设置里面，它",
                startMs: 17700,
                endMs: 18920,
                timestampMs: null,
                confidence: null,
                pageBreakAfter: true,
              },
            ]}
          />
        </Series.Sequence>
        <Series.Sequence name="精简口头修正" trimBefore={1170} durationInFrames={56} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
          <BasicCaptions
            name="精简口头修正字幕"
            premountFor={fps}
            width={940}
            style={{ position: "absolute", bottom: 220, left: 70 }}
            captions={[
              {
                text: "是引用的",
                startMs: 19500,
                endMs: 20430,
                timestampMs: null,
                confidence: null,
                pageBreakAfter: true,
              },
            ]}
          />
        </Series.Sequence>
        <Series.Sequence name="电视原生系统" trimBefore={1308} durationInFrames={150} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
          <BasicCaptions
            name="电视原生系统字幕"
            premountFor={fps}
            width={940}
            style={{ position: "absolute", bottom: 220, left: 70 }}
            captions={[
              {
                text: "本地电视机的操作系统",
                startMs: 21840,
                endMs: 24240,
                timestampMs: null,
                confidence: null,
                pageBreakAfter: true,
              },
            ]}
          />
        </Series.Sequence>
        <Series.Sequence name="壁纸来源" trimBefore={2051} durationInFrames={211} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
          <BasicCaptions
            name="壁纸来源字幕"
            premountFor={fps}
            width={940}
            style={{ position: "absolute", bottom: 220, left: 70 }}
            captions={[
              {
                text: "然后壁纸呢",
                startMs: 34260,
                endMs: 35480,
                timestampMs: null,
                confidence: null,
                pageBreakAfter: true,
              },
              {
                text: "是从我 Mac 上直接取的",
                startMs: 35780,
                endMs: 37550,
                timestampMs: null,
                confidence: null,
                pageBreakAfter: true,
              },
            ]}
          />
        </Series.Sequence>
        <Series.Sequence name="功能保持简单" trimBefore={2289} durationInFrames={154} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
          <BasicCaptions
            name="功能保持简单字幕"
            premountFor={fps}
            width={940}
            style={{ position: "absolute", bottom: 220, left: 70 }}
            captions={[
              {
                text: "所以功能上做得比较简单",
                startMs: 38260,
                endMs: 40600,
                timestampMs: null,
                confidence: null,
                pageBreakAfter: true,
              },
            ]}
          />
        </Series.Sequence>
        <Series.Sequence name="应用隐藏和删除" trimBefore={2519} durationInFrames={307} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
          <BasicCaptions
            name="应用隐藏和删除字幕"
            premountFor={fps}
            width={940}
            style={{ position: "absolute", bottom: 220, left: 70 }}
            captions={[
              {
                text: "然后预设的这些软件",
                startMs: 42140,
                endMs: 43780,
                timestampMs: null,
                confidence: null,
                pageBreakAfter: true,
              },
              {
                text: "也可以隐藏掉",
                startMs: 43780,
                endMs: 44720,
                timestampMs: null,
                confidence: null,
                pageBreakAfter: true,
              },
              {
                text: "甚至从后台进行删除掉",
                startMs: 45060,
                endMs: 47020,
                timestampMs: null,
                confidence: null,
                pageBreakAfter: true,
              },
            ]}
          />
        </Series.Sequence>
        <Series.Sequence name="飞牛应用演示" trimBefore={3030} durationInFrames={168} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
          <BasicCaptions
            name="飞牛应用演示字幕"
            premountFor={fps}
            width={940}
            style={{ position: "absolute", bottom: 220, left: 70 }}
            captions={[
              {
                text: "比如飞牛",
                startMs: 50660,
                endMs: 51650,
                timestampMs: null,
                confidence: null,
                pageBreakAfter: true,
              },
            ]}
          />
        </Series.Sequence>
        <Series.Sequence name="老人孩子易操作" trimBefore={3342} durationInFrames={270} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
          <BasicCaptions
            name="老人孩子易操作字幕"
            premountFor={fps}
            width={940}
            style={{ position: "absolute", bottom: 220, left: 70 }}
            captions={[
              {
                text: "所以说老人或者是孩子",
                startMs: 55800,
                endMs: 58340,
                timestampMs: null,
                confidence: null,
                pageBreakAfter: true,
              },
              {
                text: "操作起来就很简单",
                startMs: 58340,
                endMs: 60000,
                timestampMs: null,
                confidence: null,
                pageBreakAfter: true,
              },
            ]}
          />
        </Series.Sequence>
        <Series.Sequence name="邀请建议" trimBefore={4155} durationInFrames={248} premountFor={fps}>
          <Video
            src={staticFile("source.mov")}
            premountFor={fps}
            objectFit="contain"
            style={{ width: "100%", height: "100%" }}
          />
          <BasicCaptions
            name="邀请建议字幕"
            premountFor={fps}
            width={940}
            style={{ position: "absolute", bottom: 220, left: 70 }}
            captions={[
              {
                text: "后续还需要加哪些功能？",
                startMs: 69360,
                endMs: 71760,
                timestampMs: null,
                confidence: null,
                pageBreakAfter: true,
              },
              {
                text: "大家可以提个建议",
                startMs: 71960,
                endMs: 73180,
                timestampMs: null,
                confidence: null,
                pageBreakAfter: true,
              },
            ]}
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
