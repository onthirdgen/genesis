import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';

import { cn } from '@/lib/utils/cn';

const skeletonVariants = cva('animate-pulse rounded-md bg-primary/10', {
  variants: {
    shape: {
      rectangle: '',
      circle: 'rounded-full',
      text: 'h-4',
    },
  },
  defaultVariants: {
    shape: 'rectangle',
  },
});

export interface SkeletonProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof skeletonVariants> {}

function Skeleton({ className, shape, ...props }: SkeletonProps) {
  return (
    <div
      className={cn(skeletonVariants({ shape }), className)}
      {...props}
    />
  );
}

export { Skeleton, skeletonVariants };
