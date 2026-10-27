export type BookEntry = { orderId: string; price: number; remainingQuantity: number; sequence: number };
export type Trade = { tradeId: string; price: number; quantity: number; executedAt: string };
export type MarketState = {
  sequence: number; stale: boolean; bids: BookEntry[]; asks: BookEntry[]; trades: Trade[];
};
export type WireMessage =
  | { type: "SNAPSHOT"; sequence: number; orderBook: { bids: BookEntry[]; asks: BookEntry[] }; trades: Trade[] }
  | { type: "TRADE_CREATED" | "ORDER_CHANGED" | "MARKET_BAR"; sequence: number; payload: Record<string, unknown> };

export const emptyMarket: MarketState = { sequence: 0, stale: true, bids: [], asks: [], trades: [] };

/** 返回 stale=true 表示增量丢失，调用者应重新请求 HTTP 快照。 */
export function reduceMarket(state: MarketState, message: WireMessage): MarketState {
  if (message.type === "SNAPSHOT") return {
    sequence: message.sequence, stale: false, bids: message.orderBook.bids,
    asks: message.orderBook.asks, trades: message.trades
  };
  if (message.sequence <= state.sequence) return state;
  if (message.sequence !== state.sequence + 1) return { ...state, stale: true };
  if (message.type === "TRADE_CREATED") {
    const trade = message.payload as unknown as Trade;
    return { ...state, sequence: message.sequence, trades: [trade, ...state.trades].slice(0, 50) };
  }
  // 订单变化会改变聚合盘口，标记为 stale 后由 HTTP 快照进行权威校正。
  return { ...state, sequence: message.sequence, stale: message.type === "ORDER_CHANGED" };
}
