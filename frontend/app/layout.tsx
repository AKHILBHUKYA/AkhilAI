import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: " AKHIL AI Search",
  description: "Real-time AI web research with citations",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen">{children}</body>
    </html>
  );
}
