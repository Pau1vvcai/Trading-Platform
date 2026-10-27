import "./globals.css";
export const metadata = { title: "Northstar Exchange", description: "模拟交易与策略回测终端" };
export default function Layout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="zh-CN"><body>{children}</body></html>;
}
