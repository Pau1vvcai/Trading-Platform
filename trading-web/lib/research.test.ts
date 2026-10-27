import { describe, expect, it } from "vitest";
import { improvementLabel, stabilityLabel } from "./research";

describe("research result presentation", () => {
  it("counts only folds where the model beats the baseline", () => {
    const fold = (baselineMae: number, modelMae: number, index: number) => ({
      fold: index, trainFrom: "", trainTo: "", testFrom: "", testTo: "",
      trainSamples: 40, testSamples: 10, baselineMae, modelMae
    });
    expect(stabilityLabel([fold(0.2, 0.1, 1), fold(0.1, 0.12, 2)])).toBe("1/2 窗口优于基线");
  });

  it("formats positive and negative improvement explicitly", () => {
    expect(improvementLabel(12.345)).toBe("+12.35%");
    expect(improvementLabel(-2)).toBe("-2.00%");
    expect(improvementLabel(undefined)).toBe("—");
  });
});
