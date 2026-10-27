"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { emptyMarket, MarketState, reduceMarket, WireMessage } from "../lib/realtime";
import { improvementLabel, ResearchResult, stabilityLabel } from "../lib/research";

type Account = { accountId: string; currency: string; availableCash: number; frozenCash: number; positions: Record<string, { available: number; frozen: number }> };
type EquityPoint = { timestamp: string; equity: number };
type BacktestJob = { status: string; progress: number; error?: string; result?: { finalEquity: number; totalReturn: number; maxDrawdown: number; winRate: number; totalFees: number; equityCurve: EquityPoint[] } };

const SYMBOLS = ["BTC-USD", "ETH-USD", "SOL-USD"];
const money = (n: number | undefined) => n == null ? "—" : Intl.NumberFormat("zh-CN", { maximumFractionDigits: 2 }).format(n);

export function TradingTerminal() {
  const [symbol, setSymbol] = useState("BTC-USD");
  const [market, setMarket] = useState<MarketState>(emptyMarket);
  const [connection, setConnection] = useState("连接中");
  const [accountId, setAccountId] = useState("");
  const [account, setAccount] = useState<Account | null>(null);
  const [side, setSide] = useState("BUY");
  const [price, setPrice] = useState("100");
  const [quantity, setQuantity] = useState("1");
  const [notice, setNotice] = useState("等待操作");
  const [strategy, setStrategy] = useState("SMA");
  const [job, setJob] = useState<BacktestJob | null>(null);
  const [mlResult, setMlResult] = useState<ResearchResult | null>(null);
  const [mlRunning, setMlRunning] = useState(false);

  const refreshBook = useCallback(async () => {
    try {
      const response = await fetch(`/api/v1/order-books/${symbol}`, { cache: "no-store" });
      if (!response.ok) throw new Error("盘口不可用");
      const orderBook = await response.json();
      setMarket(current => ({ ...current, bids: orderBook.bids, asks: orderBook.asks, stale: false }));
    } catch { setMarket(current => ({ ...current, stale: true })); }
  }, [symbol]);

  useEffect(() => {
    setMarket(emptyMarket); refreshBook();
    const configured = process.env.NEXT_PUBLIC_WS_URL;
    const protocol = location.protocol === "https:" ? "wss" : "ws";
    const url = configured ?? `${protocol}://${location.hostname}:8080/ws/market`;
    const socket = new WebSocket(`${url}?symbol=${encodeURIComponent(symbol)}`);
    socket.onopen = () => setConnection("实时");
    socket.onmessage = event => setMarket(current => reduceMarket(current, JSON.parse(event.data) as WireMessage));
    socket.onerror = () => setConnection("异常");
    socket.onclose = () => setConnection("已断开");
    return () => socket.close();
  }, [symbol, refreshBook]);

  useEffect(() => { if (market.stale) refreshBook(); }, [market.stale, refreshBook]);

  const loadResearch = useCallback(async () => {
    const response = await fetch(`/api/v1/research-results?symbol=${encodeURIComponent(symbol)}&limit=1`, { cache: "no-store" });
    if (!response.ok) return;
    const values = await response.json() as ResearchResult[];
    setMlResult(values[0] ?? null);
  }, [symbol]);

  useEffect(() => { loadResearch(); }, [loadResearch]);

  async function loadAccount() {
    try {
      const response = await fetch(`/api/v1/accounts/${accountId}`);
      if (!response.ok) throw new Error("账户不存在");
      setAccount(await response.json()); setNotice("账户已载入");
    } catch (error) { setNotice(error instanceof Error ? error.message : "载入失败"); }
  }

  async function placeOrder(event: FormEvent) {
    event.preventDefault();
    if (!accountId) { setNotice("请先填写账户 ID"); return; }
    const response = await fetch("/api/v1/orders", { method: "POST",
      headers: { "Content-Type": "application/json", "Idempotency-Key": crypto.randomUUID() },
      body: JSON.stringify({ accountId, symbol, side, type: "LIMIT", limitPrice: Number(price), quantity: Number(quantity) }) });
    const body = await response.json();
    setNotice(response.ok ? `委托已受理 · ${String(body.orderId).slice(0, 8)}` : body.message ?? "下单失败");
    if (response.ok) { refreshBook(); loadAccount(); }
  }

  async function importCsv(file: File | undefined) {
    if (!file) return;
    const data = new FormData(); data.append("file", file);
    const response = await fetch("/api/v1/market-data/import", { method: "POST", body: data });
    const result = await response.json();
    setNotice(response.ok ? `已导入 ${result.importedRows} 根行情` : "导入失败");
  }

  async function runBacktest() {
    const to = new Date(); const from = new Date(to); from.setUTCFullYear(to.getUTCFullYear() - 10);
    const parameters = strategy === "SMA" ? { shortWindow: 5, longWindow: 20 }
      : strategy === "BREAKOUT" ? { lookback: 20 } : { interval: 5, allocation: 0.2 };
    const response = await fetch("/api/v1/backtests", { method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ symbol, from: from.toISOString(), to: to.toISOString(), strategy,
        initialCapital: 100000, feeRate: 0.001, ...parameters }) });
    const body = await response.json();
    if (!response.ok) { setNotice(body.message ?? "回测提交失败"); return; }
    setNotice(`回测任务 ${body.jobId.slice(0, 8)} 已提交`); pollJob(body.jobId);
  }

  function pollJob(id: string) {
    const timer = window.setInterval(async () => {
      const response = await fetch(`/api/v1/backtests/${id}`);
      if (!response.ok) return;
      const next: BacktestJob = await response.json(); setJob(next);
      if (next.status === "SUCCEEDED" || next.status === "FAILED") window.clearInterval(timer);
    }, 500);
  }

  async function runMlExperiment() {
    setMlRunning(true); setNotice("正在执行 Walk-forward 训练与验证");
    const to = new Date(); const from = new Date(to); from.setUTCFullYear(to.getUTCFullYear() - 10);
    try {
      const response = await fetch("/ml-api/api/v1/experiments", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ symbol, from: from.toISOString(), to: to.toISOString(), model: "RIDGE", folds: 3, minTrainSize: 40 })
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.detail ?? "模型实验失败");
      setMlResult(body as ResearchResult);
      setNotice(`模型实验 ${String(body.experimentId).slice(0, 8)} 已完成并回写`);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "模型实验失败");
    } finally { setMlRunning(false); }
  }

  const spread = market.bids[0] && market.asks[0] ? Number(market.asks[0].price) - Number(market.bids[0].price) : undefined;
  const last = market.trades[0]?.price;

  return <main className="terminal-shell">
    <header className="topbar">
      <div className="brand"><span className="brand-mark">N</span><div><strong>NORTHSTAR</strong><small>SIMULATED EXCHANGE</small></div></div>
      <div className="market-heading"><select value={symbol} onChange={e => setSymbol(e.target.value)}>{SYMBOLS.map(item => <option key={item}>{item}</option>)}</select><b>{money(last)}</b><span>点差 {money(spread)}</span></div>
      <div className={`connection ${connection === "实时" ? "live" : ""}`}><i />{connection} · #{market.sequence}</div>
    </header>

    <section className="workspace">
      <aside className="rail">
        <label className="eyebrow">观察列表</label>
        {SYMBOLS.map((item, index) => <button className={item === symbol ? "watch active" : "watch"} key={item} onClick={() => setSymbol(item)}><span>{item}</span><small>{index === 0 ? "+2.41%" : index === 1 ? "−0.82%" : "+1.06%"}</small></button>)}
        <div className="rule" />
        <label className="eyebrow">模拟账户</label>
        <input className="compact-input" value={accountId} onChange={e => setAccountId(e.target.value)} placeholder="粘贴 Account UUID" />
        <button className="text-button" onClick={loadAccount}>载入账户 →</button>
        <dl className="account-grid"><div><dt>可用资金</dt><dd>{money(account?.availableCash)}</dd></div><div><dt>冻结</dt><dd>{money(account?.frozenCash)}</dd></div><div><dt>持仓</dt><dd>{money(account?.positions[symbol]?.available)}</dd></div></dl>
      </aside>

      <section className="tape-panel">
        <div className="section-title"><div><span>MARKET TAPE</span><h1>价格优先 · 时间优先</h1></div><button onClick={refreshBook}>校准快照</button></div>
        <div className="book-head"><span>方向 / 序号</span><span>价格</span><span>剩余数量</span></div>
        <div className="book asks">{market.asks.slice().reverse().slice(0, 8).map(row => <BookRow key={row.orderId} side="ASK" price={row.price} quantity={row.remainingQuantity} sequence={row.sequence} />)}</div>
        <div className="midline"><span>LAST</span><strong>{money(last)}</strong><small>SPREAD {money(spread)}</small></div>
        <div className="book bids">{market.bids.slice(0, 8).map(row => <BookRow key={row.orderId} side="BID" price={row.price} quantity={row.remainingQuantity} sequence={row.sequence} />)}</div>
        {!market.bids.length && !market.asks.length && <div className="empty">盘口暂无委托，提交买卖单后会在此出现。</div>}
      </section>

      <aside className="order-panel">
        <div className="section-title"><div><span>ORDER ENTRY</span><h2>限价委托</h2></div></div>
        <form onSubmit={placeOrder}>
          <div className="side-toggle"><button type="button" className={side === "BUY" ? "buy selected" : "buy"} onClick={() => setSide("BUY")}>买入</button><button type="button" className={side === "SELL" ? "sell selected" : "sell"} onClick={() => setSide("SELL")}>卖出</button></div>
          <label>价格 <input value={price} inputMode="decimal" onChange={e => setPrice(e.target.value)} /><em>USD</em></label>
          <label>数量 <input value={quantity} inputMode="decimal" onChange={e => setQuantity(e.target.value)} /><em>{symbol.split("-")[0]}</em></label>
          <div className="estimate"><span>预计金额</span><strong>{money(Number(price) * Number(quantity))} USD</strong></div>
          <button className={`submit ${side.toLowerCase()}`} type="submit">提交{side === "BUY" ? "买入" : "卖出"}委托</button>
        </form>
        <div className="notice"><span>系统回执</span><p>{notice}</p></div>
        <div className="trade-feed"><label className="eyebrow">最新成交</label>{market.trades.slice(0, 5).map((trade, i) => <div key={trade.tradeId ?? i}><time>{trade.executedAt?.slice(11, 19)}</time><b>{money(Number(trade.price))}</b><span>{money(Number(trade.quantity))}</span></div>)}</div>
      </aside>
    </section>

    <section className="research">
      <div className="research-controls"><div><span className="eyebrow">RESEARCH DESK</span><h2>策略回测</h2><p>在已导入的 OHLCV 数据上执行确定性、含手续费的多空信号模拟。</p></div><label className="upload">导入 CSV<input type="file" accept=".csv,text/csv" onChange={e => importCsv(e.target.files?.[0])} /></label><select value={strategy} onChange={e => setStrategy(e.target.value)}><option value="SMA">双均线</option><option value="BREAKOUT">区间突破</option><option value="DCA">定投</option></select><button className="run" onClick={runBacktest}>运行回测</button></div>
      <div className="results">
        <Metric label="任务状态" value={job ? `${job.status} · ${job.progress}%` : "尚未运行"} />
        <Metric label="最终权益" value={money(job?.result?.finalEquity)} />
        <Metric label="总收益率" value={job?.result ? `${(job.result.totalReturn * 100).toFixed(2)}%` : "—"} />
        <Metric label="最大回撤" value={job?.result ? `${(job.result.maxDrawdown * 100).toFixed(2)}%` : "—"} />
        <Metric label="胜率 / 手续费" value={job?.result ? `${(job.result.winRate * 100).toFixed(1)}% / ${money(job.result.totalFees)}` : "—"} />
        <EquityCurve points={job?.result?.equityCurve ?? []} />
      </div>
      <div className="ml-research">
        <div className="research-controls ml-controls">
          <div><span className="eyebrow">ML LAB · WALK-FORWARD</span><h2>下一周期波动率研究</h2><p>使用当前及历史 K 线特征预测下一根绝对收益率；缩放与训练严格限制在每个训练窗口内。</p></div>
          <button className="run" disabled={mlRunning} onClick={runMlExperiment}>{mlRunning ? "训练中…" : "运行 Ridge 实验"}</button>
        </div>
        <div className="results ml-results">
          <Metric label="数据集版本" value={mlResult?.datasetVersion ?? "尚未运行"} />
          <Metric label="样本 / 窗口" value={mlResult ? `${mlResult.sampleCount} / ${mlResult.foldCount}` : "—"} />
          <Metric label="基线 MAE" value={mlResult ? mlResult.baselineMae.toFixed(6) : "—"} />
          <Metric label="模型 MAE" value={mlResult ? mlResult.modelMae.toFixed(6) : "—"} />
          <Metric label="相对基线改善" value={improvementLabel(mlResult?.improvementPct)} />
          <div className="ml-audit">
            <div><span>跨时段稳定性</span><strong>{stabilityLabel(mlResult?.folds ?? [])}</strong></div>
            <div><span>防泄漏规则</span><p>{mlResult?.leakagePolicy ?? "等待实验结果"}</p></div>
            <div><span>费用与滑点</span><p>{mlResult?.costAssumption ?? "等待实验结果"}</p></div>
          </div>
          {!!mlResult?.folds.length && <div className="fold-table"><div className="fold-head"><span>窗口</span><span>训练截止</span><span>测试区间</span><span>基线 MAE</span><span>模型 MAE</span></div>{mlResult.folds.map(fold => <div key={fold.fold}><b>#{fold.fold}</b><span>{fold.trainTo.slice(0, 10)}</span><span>{fold.testFrom.slice(0, 10)} → {fold.testTo.slice(0, 10)}</span><span>{fold.baselineMae.toFixed(6)}</span><span>{fold.modelMae.toFixed(6)}</span></div>)}</div>}
        </div>
      </div>
    </section>
  </main>;
}

function BookRow({ side, price, quantity, sequence }: { side: string; price: number; quantity: number; sequence: number }) {
  const width = Math.min(96, 12 + Number(quantity) * 8);
  return <div className="book-row" style={{ "--depth": `${width}%` } as React.CSSProperties}><span>{side} <small>#{sequence}</small></span><b>{money(Number(price))}</b><em>{money(Number(quantity))}</em></div>;
}
function Metric({ label, value }: { label: string; value: string }) { return <div className="metric"><span>{label}</span><strong>{value}</strong></div>; }
function EquityCurve({ points }: { points: EquityPoint[] }) {
  const path = useMemo(() => {
    if (points.length < 2) return ""; const values = points.map(p => Number(p.equity));
    const min = Math.min(...values), max = Math.max(...values), range = max - min || 1;
    return values.map((value, i) => `${i ? "L" : "M"}${(i / (values.length - 1)) * 600},${112 - ((value - min) / range) * 96}`).join(" ");
  }, [points]);
  return <div className="chart"><span>权益曲线</span>{path ? <svg viewBox="0 0 600 128" preserveAspectRatio="none"><path d={path} /></svg> : <div className="chart-empty">导入行情并运行回测后生成曲线</div>}</div>;
}
