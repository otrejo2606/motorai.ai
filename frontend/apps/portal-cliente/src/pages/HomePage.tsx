import { useEffect, useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  FUELS,
  LISTING_SORTS,
  PROVINCES,
  TRANSMISSIONS,
  TRUST_LEVELS,
  useListings,
  type ListingSearchParams,
  type ListingSummary,
} from '@motoria/api';
import {
  Button,
  EmptyState,
  ErrorAlert,
  Loading,
  PageTitle,
  Pagination,
  SelectField,
  TextField,
  TrustBadge,
  useEnumOptions,
  useFieldError,
  useFormat,
} from '@motoria/ui';
import { appConfig } from '../config';

const digits = z.string().trim().regex(/^[0-9]*$/, 'validation.integer');
const text = z.string().trim().max(60, 'validation.tooLong');

const filterSchema = z
  .object({
    brand: text,
    model: text,
    yearFrom: digits,
    yearTo: digits,
    minPrice: digits,
    maxPrice: digits,
    maxMileage: digits,
    province: z.union([z.enum(PROVINCES), z.literal('')]),
    fuel: z.union([z.enum(FUELS), z.literal('')]),
    transmission: z.union([z.enum(TRANSMISSIONS), z.literal('')]),
    trustLevel: z.union([z.enum(TRUST_LEVELS), z.literal('')]),
    sort: z.enum(LISTING_SORTS),
  })
  .superRefine((values, ctx) => {
    if (values.yearFrom && values.yearTo && Number(values.yearFrom) > Number(values.yearTo)) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['yearTo'], message: 'validation.rangeInvalid' });
    }
    if (values.minPrice && values.maxPrice && Number(values.minPrice) > Number(values.maxPrice)) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['maxPrice'], message: 'validation.rangeInvalid' });
    }
  });

type FilterValues = z.infer<typeof filterSchema>;

const TEXT_KEYS = ['brand', 'model', 'yearFrom', 'yearTo', 'minPrice', 'maxPrice', 'maxMileage'] as const;

function oneOf<T extends string>(values: readonly T[], value: string | null): T | '' {
  return value !== null && (values as readonly string[]).includes(value) ? (value as T) : '';
}

function paramsToFilters(params: URLSearchParams): FilterValues {
  return {
    brand: params.get('brand') ?? '',
    model: params.get('model') ?? '',
    yearFrom: params.get('yearFrom') ?? '',
    yearTo: params.get('yearTo') ?? '',
    minPrice: params.get('minPrice') ?? '',
    maxPrice: params.get('maxPrice') ?? '',
    maxMileage: params.get('maxMileage') ?? '',
    province: oneOf(PROVINCES, params.get('province')),
    fuel: oneOf(FUELS, params.get('fuel')),
    transmission: oneOf(TRANSMISSIONS, params.get('transmission')),
    trustLevel: oneOf(TRUST_LEVELS, params.get('trustLevel')),
    sort: oneOf(LISTING_SORTS, params.get('sort')) || 'RELEVANCE',
  };
}

function toNumber(value: string): number | undefined {
  return value && /^[0-9]+$/.test(value) ? Number(value) : undefined;
}

function filtersToQuery(values: FilterValues, page: number): ListingSearchParams {
  return {
    brand: values.brand || undefined,
    model: values.model || undefined,
    yearFrom: toNumber(values.yearFrom),
    yearTo: toNumber(values.yearTo),
    minPrice: toNumber(values.minPrice),
    maxPrice: toNumber(values.maxPrice),
    maxMileage: toNumber(values.maxMileage),
    province: values.province || undefined,
    fuel: values.fuel || undefined,
    transmission: values.transmission || undefined,
    trustLevel: values.trustLevel || undefined,
    sort: values.sort,
    page,
    size: appConfig.pageSize,
  };
}

function ListingCard({ listing }: { listing: ListingSummary }) {
  const { t } = useTranslation();
  const format = useFormat(appConfig.currency);
  const name =
    listing.title || [listing.brand, listing.model, listing.modelYear].filter(Boolean).join(' ') || t('listing.untitled');
  return (
    <article className="m-card">
      {listing.coverPhotoUrl ? (
        <img className="m-photo" src={listing.coverPhotoUrl} alt={name} loading="lazy" />
      ) : null}
      <h2 className="m-card__title">
        <Link to={'/listings/' + encodeURIComponent(listing.id)}>{name}</Link>
      </h2>
      <p>
        <strong>{format.price(listing.price, listing.currency)}</strong>
      </p>
      <p>
        {[
          listing.modelYear ? String(listing.modelYear) : '',
          listing.mileage !== null && listing.mileage !== undefined ? t('listing.mileageValue', { value: format.number(listing.mileage) }) : '',
          listing.province ? t('province.' + listing.province) : '',
        ]
          .filter(Boolean)
          .join(' · ')}
      </p>
      <TrustBadge level={listing.trustLevel} />
    </article>
  );
}

export function HomePage() {
  const { t } = useTranslation();
  const fieldError = useFieldError();
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = useMemo(() => paramsToFilters(searchParams), [searchParams]);
  const page = Math.max(0, Number(searchParams.get('page') ?? '0') || 0);
  const query = useMemo(() => filtersToQuery(filters, page), [filters, page]);
  const listings = useListings(query);

  const provinceOptions = useEnumOptions(PROVINCES, 'province');
  const fuelOptions = useEnumOptions(FUELS, 'fuel');
  const transmissionOptions = useEnumOptions(TRANSMISSIONS, 'transmission');
  const trustOptions = useEnumOptions(TRUST_LEVELS, 'trust');
  const sortOptions = useEnumOptions(LISTING_SORTS, 'sort');

  const form = useForm<FilterValues>({ resolver: zodResolver(filterSchema), defaultValues: filters });
  const { register, handleSubmit, reset, formState } = form;

  useEffect(() => {
    reset(filters);
  }, [filters, reset]);

  const onSubmit = (values: FilterValues) => {
    const next = new URLSearchParams();
    for (const [key, value] of Object.entries(values)) {
      if (value) next.set(key, String(value));
    }
    setSearchParams(next);
  };

  const changePage = (nextPage: number) => {
    const next = new URLSearchParams(searchParams);
    next.set('page', String(nextPage));
    setSearchParams(next);
    window.scrollTo({ top: 0 });
  };

  return (
    <>
      <PageTitle subtitle={t('home.subtitle')}>{t('home.title')}</PageTitle>
      <form className="m-card m-form" onSubmit={handleSubmit(onSubmit)} role="search" aria-label={t('home.searchLabel')} noValidate>
        <div className="m-form-grid m-form-grid--wide">
          {TEXT_KEYS.map((key) => (
            <TextField
              key={key}
              label={t('filters.' + key)}
              inputMode={key === 'brand' || key === 'model' ? 'text' : 'numeric'}
              autoComplete="off"
              error={fieldError(formState.errors[key])}
              {...register(key)}
            />
          ))}
          <SelectField label={t('filters.province')} placeholder={t('filters.any')} options={provinceOptions} {...register('province')} />
          <SelectField label={t('filters.fuel')} placeholder={t('filters.any')} options={fuelOptions} {...register('fuel')} />
          <SelectField
            label={t('filters.transmission')}
            placeholder={t('filters.any')}
            options={transmissionOptions}
            {...register('transmission')}
          />
          <SelectField label={t('filters.trustLevel')} placeholder={t('filters.any')} options={trustOptions} {...register('trustLevel')} />
          <SelectField label={t('filters.sort')} options={sortOptions} {...register('sort')} />
        </div>
        <div className="m-actions">
          <Button type="submit">{t('filters.apply')}</Button>
          <Button variant="secondary" onClick={() => setSearchParams(new URLSearchParams())}>
            {t('filters.clear')}
          </Button>
        </div>
      </form>

      <section aria-labelledby="results-title" aria-busy={listings.isFetching}>
        <h2 id="results-title">
          {listings.data ? t('home.resultsCount', { count: listings.data.total }) : t('home.results')}
        </h2>
        {listings.isLoading ? <Loading /> : null}
        <ErrorAlert error={listings.error} />
        {listings.data && listings.data.items.length === 0 ? <EmptyState>{t('home.noResults')}</EmptyState> : null}
        {listings.data && listings.data.items.length > 0 ? (
          <>
            <div className="m-grid">
              {listings.data.items.map((listing) => (
                <ListingCard key={listing.id} listing={listing} />
              ))}
            </div>
            <Pagination page={listings.data.page} size={listings.data.size} total={listings.data.total} onChange={changePage} />
          </>
        ) : null}
      </section>
    </>
  );
}
