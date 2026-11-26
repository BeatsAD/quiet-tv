import type { Metadata, PhoneClient, Value } from './api';
import { fileSize, Group, ResourceImage, Row } from './widgets';

const installMessages: Record<string, string> = {
  preparing: '正在准备安装',
  prepared: '请在电视上继续安装',
  file_dispatched: '已打开电视安装器，请在电视上确认',
  committing: '正在提交安装',
  submitted: '已提交安装，等待电视处理',
  confirmation: '请在电视上确认安装',
  confirming: '正在等待电视安装结果',
  awaiting_version: '等待核对电视上的实际版本',
  success: '更新已安装',
  failed: '安装失败',
  cancelled: '安装已取消',
  interrupted: '安装已中断，请核对实际版本',
  unknown: '安装结果待核对',
};
const checkable = ['file_dispatched', 'submitted', 'awaiting_version', 'interrupted', 'unknown'];

export function UpdateSettings({client, device, busy, action, install}: {
  client: PhoneClient; device?: Metadata['device']; busy: boolean;
  action: (field: string, value?: Value) => void; install: (sha: string) => void;
}) {
  const update = client.frame?.domains.update || {};
  const available = update.updateAvailable as {version: string; build: number; bytes: number; sha256: string; features: string[]; fixes: string[]} | undefined;
  const checking = !!update.updateChecking, downloading = !!update.updateDownloading;
  const verifying = !!update.updateVerifying;
  const state = String(update.updateInstallState || '');
  const connected = client.status === 'connected';
  const current = device && `${device.version}  构建 ${device.build}`;
  const installInProgress = !!update.updateInstallBusy || (state in installMessages && !['success', 'failed', 'cancelled', 'interrupted', 'unknown'].includes(state));
  const downloadLabel = verifying ? '正在校验安装包' :
    `正在下载 ${fileSize(Number(update.updateReceived))} / ${fileSize(Number(update.updateTotal))}`;
  const checkMessage = checking ? '正在检查更新…' : downloading || verifying ? downloadLabel :
    String(update.updateUnavailable || update.updateCheckError || update.updateDownloadError || (available ? '' : '按当前电视版本与渠道检查更新。'));
  return <>
    <div className="version-mark">
      <ResourceImage className="assistant-launcher-icon" src={client.resource('assets/quiettv-assistant-launcher.png')} alt=""/>
      <strong>QuietTV</strong><span>{current}</span>
    </div>
    {checkMessage && <p className="sheet-intro update-notice" role="status">{checkMessage}</p>}
    {available && <Group title={`可更新至 ${available.version}  构建 ${available.build}`}>
      <Row title="安装包" value={fileSize(available.bytes)}/>
      {(['features', 'fixes'] as const).flatMap(key => (available[key] || []).map((text, index) =>
        <Row key={`${key}${index}`} title={text}/>))}
    </Group>}
    {downloading && <progress className="update-progress" max={Number(update.updateTotal) || 1} value={Number(update.updateReceived) || 0}/>}
    {state && <div role="status">
      <p className="section-note">{connected ? installMessages[state] || '安装结果待核对' : '电视正在重新连接。连接后可核对实际版本。'}</p>
      {connected && !!update.updateInstallError && state !== 'success' && <p className="section-note">{String(update.updateInstallError)}</p>}
    </div>}
    {client.status === 'ended' ? <a className="secondary-button" href="?tab=help&tool=update">重新连接并核对版本</a> : <>
      <button className="primary-button" disabled={busy || checking || downloading || !connected}
        onClick={() => action('check')}>{checking ? '正在检查…' : '检查更新'}</button>
      {checking && <button className="secondary-button" disabled={busy || !connected} onClick={() => action('cancelCheck')}>取消检查</button>}
      {available && !checking && !downloading && !verifying && !update.updateReady &&
        <button className="secondary-button" disabled={busy || !connected} onClick={() => action('download', available.sha256)}>下载安装包</button>}
      {downloading && <button className="secondary-button" disabled={busy || !connected} onClick={() => action('cancelDownload')}>取消下载</button>}
      {available && !!update.updateReady && !checking && !verifying && <>
        {connected && !installInProgress && !!update.updateInstallPermissionRequired &&
          <p className="section-note">请在电视上允许 Quiet TV 安装更新。</p>}
        <button className="secondary-button" disabled={busy || installInProgress || !connected}
          onClick={() => install(available.sha256)}>在电视安装更新</button>
      </>}
      {checkable.includes(state) && <button className="secondary-button" disabled={busy || !connected}
        onClick={() => action('reconcile')}>核对电视版本</button>}
    </>}
  </>;
}
