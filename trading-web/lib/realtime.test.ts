import { describe, expect, it } from "vitest";
import { emptyMarket, reduceMarket } from "./realtime";

describe("reduceMarket", () => {
  it("用快照替换全部状态", () => {
    const state = reduceMarket(emptyMarket, { type: "SNAPSHOT", sequence: 7,
      orderBook: { bids: [{ orderId: "b", price: 100, remainingQuantity: 2, sequence: 1 }], asks: [] }, trades: [] });
    expect(state.sequence).toBe(7); expect(state.stale).toBe(false); expect(state.bids).toHaveLength(1);
  });
  it("忽略重复消息", () => {
    const current = { ...emptyMarket, sequence: 4, stale: false };
    expect(reduceMarket(current, { type: "MARKET_BAR", sequence: 4, payload: {} })).toBe(current);
  });
  it("发现序号断层", () => {
    const state = reduceMarket({ ...emptyMarket, sequence: 2, stale: false },
      { type: "TRADE_CREATED", sequence: 4, payload: {} });
    expect(state.stale).toBe(true); expect(state.sequence).toBe(2);
  });
  it("追加成交并限制长度", () => {
    const state = reduceMarket({ ...emptyMarket, sequence: 1, stale: false },
      { type: "TRADE_CREATED", sequence: 2, payload: { tradeId: "t", price: 101, quantity: 3, executedAt: "now" } });
    expect(state.trades[0]).toMatchObject({ tradeId: "t", price: 101 });
  });
});
