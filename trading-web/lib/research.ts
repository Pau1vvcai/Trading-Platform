export type FoldMetric = {
  fold: number;
  trainFrom: string;
  trainTo: string;
  testFrom: string;
  testTo: string;
  trainSamples: number;
  testSamples: number;
  baselineMae: number;
  modelMae: number;
};

export type ResearchResult = {
  experimentId: string;
  datasetVersion: string;
  target: string;
  model: string;
  sampleCount: number;
  foldCount: number;
  baselineMae: number;
  modelMae: number;
  improvementPct: number;
  createdAt: string;
  leakagePolicy: string;
  costAssumption: string;
  folds: FoldMetric[];
};

export function stabilityLabel(folds: FoldMetric[]): string {
  if (!folds.length) return "尚无结果";
  const wins = folds.filter(fold => fold.modelMae < fold.baselineMae).length;
  return `${wins}/${folds.length} 窗口优于基线`;
}

export function improvementLabel(value: number | undefined): string {
  if (value == null || !Number.isFinite(value)) return "—";
  return `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}
